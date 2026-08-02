package com.arny.habrrss.data.rss

import com.arny.habrrss.domain.models.ArticleContent
import com.arny.habrrss.domain.models.Author
import com.arny.habrrss.domain.models.CommentNode
import com.arny.habrrss.domain.models.FeedDescriptor
import com.arny.habrrss.domain.models.FeedItem
import com.arny.habrrss.domain.models.FeedPage
import com.arny.habrrss.domain.models.Hub
import com.arny.habrrss.domain.models.PageCursor
import com.arny.habrrss.domain.models.Tag
import com.arny.habrrss.domain.source.FeedSource
import com.arny.habrrss.domain.source.SourceUnavailableException
import com.arny.habrrss.domain.util.toEpochMillis
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.parser.Parser
import io.ktor.client.HttpClient
import kotlin.time.Clock
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText

class GenericRssSource(
    private val client: HttpClient? = null,
    private var descriptors: List<FeedDescriptor> = emptyList(),
) : FeedSource {
    constructor(descriptors: List<FeedDescriptor>) : this(client = null, descriptors = descriptors)

    fun setFeeds(feeds: List<FeedDescriptor>) {
        descriptors = feeds
    }

    override suspend fun getFeeds(): List<FeedDescriptor> = descriptors

    override suspend fun getItems(feedId: String, page: PageCursor?): FeedPage {
        val httpClient = client ?: return emptyPage()
        val descriptor = descriptors.firstOrNull { it.id == feedId } ?: return emptyPage()
        val xml = httpClient.get(descriptor.url).bodyAsText()
        val doc = Ksoup.parse(xml, Parser.xmlParser())
        val items = doc.select("item, entry").map { it.toFeedItem(feedId) }
        return FeedPage(
            items = items,
            nextCursor = null,
            fromCache = false,
            updatedAt = "${Clock.System.now().toEpochMilliseconds()}",
        )
    }

    private fun emptyPage(): FeedPage = FeedPage(
        items = emptyList(),
        nextCursor = null,
        fromCache = true,
        updatedAt = null,
    )

    private fun Element.toFeedItem(feedId: String): FeedItem {
        val title = rssText("title")?.takeIf { it.isNotBlank() } ?: "Без заголовка"
        val link = rssText("link")
            ?: selectFirst("link")?.attr("href")?.takeIf { it.isNotBlank() }
            ?: ""
        val descriptionHtml = rssHtml("description")
            .ifBlank { rssHtml("content") }
            .ifBlank { rssHtml("summary") }
        val descDoc = Ksoup.parseBodyFragment(descriptionHtml)
        val categories = select("category").mapNotNull { category ->
            category.text().ifBlank { category.attr("term") }.trim().takeIf { it.isNotBlank() }
        }.distinct()
        val authorName = rssText("author") ?: selectFirst("author name")?.text()?.trim()
        val publishedAt = rssText("pubDate") ?: rssText("published") ?: rssText("updated")

        return FeedItem(
            id = link.takeIf { it.isNotBlank() }?.let { "custom-${it.hashCode()}" } ?: "custom-${title.hashCode()}",
            feedId = feedId,
            title = title,
            summary = descDoc.text().trim(),
            descriptionHtml = descriptionHtml,
            url = link,
            imageUrl = descDoc.selectFirst("img")?.attr("src")?.takeIf { it.isNotBlank() },
            author = authorName?.let { Author(id = "author-${it.hashCode()}", displayName = it, profileUrl = null) },
            publishedAt = publishedAt,
            publishedAtEpoch = publishedAt?.toEpochMillis(),
            tags = categories.map { Tag(id = "tag-${it.lowercase().hashCode()}", title = it) },
            hubs = emptyList<Hub>(),
            rating = null,
            commentsCount = null,
            isRead = false,
            isBookmarked = false,
        )
    }

    private fun Element.rssText(tagName: String): String? {
        val node = selectFirst(tagName) ?: return null
        return cleanRssText(node.text().ifBlank { node.html() })
    }

    private fun Element.rssHtml(tagName: String): String {
        val node = selectFirst(tagName) ?: return ""
        return cleanRssText(node.html().ifBlank { node.text() })
    }

    private fun cleanRssText(value: String): String = value.trim()
        .removePrefix("<![CDATA[")
        .removePrefix("![CDATA[")
        .removeSuffix("]]>")
        .removeSuffix("]]")
        .trim()

    @Deprecated("Use ArticleContentSource instead", ReplaceWith("ArticleContentSource"))
    override suspend fun getArticle(articleId: String): ArticleContent {
        throw SourceUnavailableException("Generic RSS article body loading is not implemented yet.")
    }

    override suspend fun getComments(articleId: String): List<CommentNode> = emptyList()
}
