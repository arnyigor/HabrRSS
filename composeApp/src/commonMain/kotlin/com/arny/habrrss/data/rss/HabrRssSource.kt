package com.arny.habrrss.data.rss

import com.arny.habrrss.data.article.HabrArticleContentExtractor
import com.arny.habrrss.domain.models.ArticleContent
import com.arny.habrrss.domain.models.Author
import com.arny.habrrss.domain.models.CommentNode
import com.arny.habrrss.domain.models.FeedDescriptor
import com.arny.habrrss.domain.models.FeedItem
import com.arny.habrrss.domain.models.FeedKind
import com.arny.habrrss.domain.models.FeedPage
import com.arny.habrrss.domain.models.Hub
import com.arny.habrrss.domain.models.PageCursor
import com.arny.habrrss.domain.models.Tag
import com.arny.habrrss.domain.source.FeedSource
import com.arny.habrrss.domain.source.SourceUnavailableException
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.parser.Parser
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText

class HabrRssSource(
    private val client: HttpClient,
    private val articleExtractor: HabrArticleContentExtractor = HabrArticleContentExtractor(),
) : FeedSource {

    private val feeds = listOf(
        FeedDescriptor(
            id = FeedIds.All,
            title = "Все публикации",
            sourceTitle = "Habr",
            url = "https://habr.com/ru/rss/articles/?limit=100&with_hubs=true&with_tags=true",
            description = "Все статьи Хабра",
            kind = FeedKind.All
        ),
        FeedDescriptor(
            id = FeedIds.Best,
            title = "Лучшее за день",
            sourceTitle = "Habr",
            url = "https://habr.com/ru/rss/best/daily/?limit=100&with_hubs=true&with_tags=true",
            description = "Лучшие статьи за сутки",
            kind = FeedKind.Best
        ),
        FeedDescriptor(
            id = FeedIds.Posts,
            title = "Посты",
            sourceTitle = "Habr",
            url = "https://habr.com/ru/rss/posts/?limit=100&with_hubs=true&with_tags=true",
            description = "Посты Хабра",
            kind = FeedKind.Posts
        ),
        FeedDescriptor(
            id = FeedIds.News,
            title = "Новости",
            sourceTitle = "Habr",
            url = "https://habr.com/ru/rss/news/?limit=100&with_hubs=true&with_tags=true",
            description = "Новости Хабра",
            kind = FeedKind.News
        ),
        FeedDescriptor(
            id = FeedIds.Kotlin,
            title = "Kotlin",
            sourceTitle = "Habr",
            url = "https://habr.com/ru/rss/hub/kotlin/?limit=100&with_hubs=true&with_tags=true",
            description = "Хаб Kotlin",
            kind = FeedKind.Hub
        )
    )

    override suspend fun getFeeds(): List<FeedDescriptor> = feeds

    override suspend fun getItems(feedId: String, page: PageCursor?): FeedPage {
        val descriptor = feeds.find { it.id == feedId }
            ?: return FeedPage(emptyList(), null, false, null)

        val xml = client.get(descriptor.url).bodyAsText()
        val doc = Ksoup.parse(xml, Parser.xmlParser())
        val items = doc.select("item").map { parseItem(it, feedId) }

        return FeedPage(
            items = items,
            nextCursor = null,
            fromCache = false,
            updatedAt = "${System.currentTimeMillis()}"
        )
    }

    private fun parseItem(element: Element, feedId: String): FeedItem {
        val title = element.rssText("title")?.takeIf { it.isNotBlank() }
            ?: "Без заголовка"
        val link = element.rssText("link") ?: ""
        val guid = element.rssText("guid")?.takeIf { it.isNotBlank() }
            ?: link.ifBlank { "guid-${System.currentTimeMillis()}" }
        val pubDate = element.rssText("pubDate")
        val descriptionHtml = element.rssHtml("description")
        val authorName = element.rssText("author")
            ?: element.rssText("dc|creator")

        val parsedMetadata = parseDescriptionMetadata(descriptionHtml)
        val categories = element.select("category").map { it.text().trim() }.filter { it.isNotBlank() }
        val hubTitles = parsedMetadata.hubs.ifEmpty { categories }
        val tagTitles = parsedMetadata.tags

        val hubs = hubTitles.distinct().map { title ->
            Hub(id = title.stableMetadataId(prefix = "hub"), title = title)
        }
        val tags = tagTitles.distinct().map { title ->
            Tag(id = title.stableMetadataId(prefix = "tag"), title = title)
        }

        val descDoc = Ksoup.parseBodyFragment(descriptionHtml)
        val imgUrl = descDoc.selectFirst("img")?.attr("src")?.takeIf { it.isNotBlank() }
        val plainText = descDoc.text().trim()

        return FeedItem(
            id = guid,
            feedId = feedId,
            title = title,
            summary = plainText,
            descriptionHtml = descriptionHtml,
            url = link,
            imageUrl = imgUrl,
            author = authorName?.let {
                Author(id = "author-${it.hashCode()}", displayName = it, profileUrl = null)
            },
            publishedAt = pubDate,
            tags = tags,
            hubs = hubs,
            rating = null,
            commentsCount = null,
            isRead = false,
            isBookmarked = false
        )
    }

    override suspend fun getArticle(articleId: String): ArticleContent {
        val articleUrl = normalizeArticleUrl(articleId)
        val html = client.get(articleUrl).bodyAsText()
        return articleExtractor.extract(articleId = articleId, articleUrl = articleUrl, html = html)
    }

    override suspend fun getComments(articleId: String): List<CommentNode> = emptyList()

    private fun Element.rssText(tagName: String): String? {
        val node = selectFirst(tagName) ?: return null
        return cleanRssText(node.text().ifBlank { node.html() })
    }

    private fun Element.rssHtml(tagName: String): String {
        val node = selectFirst(tagName) ?: return ""
        return cleanRssText(node.html().ifBlank { node.text() })
    }

    private fun cleanRssText(value: String): String {
        return value.trim()
            .removePrefix("<![CDATA[")
            .removePrefix("![CDATA[")
            .removeSuffix("]]>")
            .removeSuffix("]]")
            .trim()
    }

    private fun parseDescriptionMetadata(descriptionHtml: String): RssMetadata {
        val text = Ksoup.parseBodyFragment(descriptionHtml).wholeText().replace('\u00A0', ' ')
        return RssMetadata(
            hubs = text.metadataSection("Хабы:", "Метки:"),
            tags = text.metadataSection("Метки:"),
        )
    }

    private fun String.metadataSection(start: String, end: String? = null): List<String> {
        val startIndex = indexOf(start)
        if (startIndex < 0) return emptyList()
        val contentStart = startIndex + start.length
        val contentEnd = listOfNotNull(
            end?.let { marker -> indexOf(marker, contentStart).takeIf { it >= 0 } },
            indexOf('\n', contentStart).takeIf { it >= 0 },
        ).minOrNull() ?: length
        return substring(contentStart, contentEnd)
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun String.stableMetadataId(prefix: String): String {
        return "$prefix-${lowercase().trim().hashCode()}"
    }

    private fun normalizeArticleUrl(articleId: String): String {
        val value = articleId.replace("&amp;", "&").trim()
        return when {
            value.startsWith("http://") || value.startsWith("https://") -> value
            value.all { it.isDigit() } -> "https://habr.com/ru/articles/$value/"
            else -> value
        }
    }

    object FeedIds {
        const val All = "habr-all"
        const val Best = "habr-best"
        const val Posts = "habr-posts"
        const val News = "habr-news"
        const val Kotlin = "habr-kotlin"
    }

    private data class RssMetadata(
        val hubs: List<String>,
        val tags: List<String>,
    )
}
