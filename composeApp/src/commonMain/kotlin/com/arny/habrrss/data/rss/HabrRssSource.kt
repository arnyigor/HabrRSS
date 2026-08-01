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
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.parser.Parser
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlin.time.Clock
import kotlinx.datetime.Month
import kotlinx.datetime.toInstant

class HabrRssSource(
    private val client: HttpClient,
    private val articleExtractor: HabrArticleContentExtractor = HabrArticleContentExtractor(),
) : FeedSource {

    private val feeds = listOf(
        FeedDescriptor(
            id = FeedIds.All,
            title = "Все статьи",
            sourceTitle = "Habr",
            url = "https://habr.com/ru/rss/articles/?limit=100&with_hubs=true&with_tags=true",
            description = "Последние 100 статей Хабра из официального RSS",
            kind = FeedKind.All,
        ),
    )

    override suspend fun getFeeds(): List<FeedDescriptor> = feeds

    override suspend fun getItems(feedId: String, page: PageCursor?): FeedPage {
        val descriptor = feeds.find { it.id == feedId }
            ?: return FeedPage(emptyList(), null, false, null)

        // Habr RSS is a latest-items feed, not a reliable paged API. Always request only the
        // official articles RSS and keep history locally in Room.
        val xml = client.get(descriptor.url).bodyAsText()
        val doc = Ksoup.parse(xml, Parser.xmlParser())
        val items = doc.select("item")
            .mapNotNull { it.toRssArticleDto() }
            .map { it.toFeedItem(feedId) }

        return FeedPage(
            items = items,
            nextCursor = null,
            fromCache = false,
            updatedAt = "${Clock.System.now().toEpochMilliseconds()}",
        )
    }

    private fun Element.toRssArticleDto(): RssArticleDto? {
        val link = rssText("link")
        val guid = rssText("guid")
        val articleUrl = listOfNotNull(link, guid)
            .firstOrNull { it.isHabrArticleUrl() }
            ?: return null
        val articleId = articleUrl.extractHabrArticleId() ?: return null
        val descriptionHtml = rssHtml("description")
        val parsedMetadata = parseDescriptionMetadata(descriptionHtml)
        val categories = select("category")
            .map { category -> cleanRssText(category.text().ifBlank { category.html() }) }
            .normalizedMetadataTitles()

        return RssArticleDto(
            id = articleId,
            title = rssText("title"),
            link = link?.takeIf { it.isHabrArticleUrl() } ?: articleUrl,
            guid = guid,
            author = rssText("author") ?: rssText("dc|creator"),
            publishedAt = rssText("pubDate"),
            descriptionHtml = descriptionHtml,
            categories = categories,
            hubs = parsedMetadata.hubs.ifEmpty { categories },
            tags = parsedMetadata.tags.ifEmpty { categories },
        )
    }

    private fun RssArticleDto.toFeedItem(feedId: String): FeedItem {
        val description = descriptionHtml.orEmpty()
        val descDoc = Ksoup.parseBodyFragment(description)
        val imgUrl = descDoc.selectFirst("img")?.attr("src")?.takeIf { it.isNotBlank() }
        val plainText = descDoc.text().trim()

        return FeedItem(
            id = id,
            feedId = feedId,
            title = title?.takeIf { it.isNotBlank() } ?: "Без заголовка",
            summary = plainText,
            descriptionHtml = description,
            url = link,
            imageUrl = imgUrl,
            author = author?.let {
                Author(id = "author-${it.hashCode()}", displayName = it, profileUrl = null)
            },
            publishedAt = publishedAt,
            publishedAtEpoch = publishedAt?.parseRfc822Date(),
            tags = tags.map { title -> Tag(id = title.stableMetadataId(prefix = "tag"), title = title) },
            hubs = hubs.map { title -> Hub(id = title.stableMetadataId(prefix = "hub"), title = title) },
            rating = null,
            commentsCount = null,
            isRead = false,
            isBookmarked = false,
        )
    }

    @Deprecated("Use ArticleContentSource instead", ReplaceWith("HabrArticleContentSource(client)"))
    override suspend fun getArticle(articleId: String): ArticleContent {
        val articleUrl = normalizeArticleUrl(articleId)
        val html = client.get(articleUrl).bodyAsText()
        return articleExtractor.extract(articleId = articleId, articleUrl = articleUrl, html = html)
    }

    override suspend fun getComments(articleId: String): List<CommentNode> = emptyList()

    private fun Element.rssText(tagName: String): String? {
        val node = selectFirst(tagName) ?: return null
        return cleanRssText(node.text().ifBlank { node.html() }).takeIf { it.isNotBlank() }
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
            hubs = text.metadataSection("Хабы:", "Метки:").normalizedMetadataTitles(),
            tags = text.metadataSection("Метки:").normalizedMetadataTitles(),
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

    private fun List<String>.normalizedMetadataTitles(): List<String> {
        val seen = mutableSetOf<String>()
        return mapNotNull { raw ->
            val title = raw.replace('\u00A0', ' ')
                .trim()
                .replace(Regex("\\s+"), " ")
                .takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val key = title.lowercase()
            if (seen.add(key)) title else null
        }
    }

    private fun String.stableMetadataId(prefix: String): String {
        val normalized = replace('\u00A0', ' ')
            .trim()
            .replace(Regex("\\s+"), " ")
            .lowercase()
        return "$prefix-${normalized.hashCode()}"
    }

    private fun normalizeArticleUrl(articleId: String): String {
        val value = articleId.replace("&amp;", "&").trim()
        return when {
            value.startsWith("http://") || value.startsWith("https://") -> value
            value.removePrefix("habr-").all { it.isDigit() } -> "https://habr.com/ru/articles/${value.removePrefix("habr-")}/"
            else -> value
        }
    }

    object FeedIds {
        const val All = "habr-all"
    }

    private data class RssArticleDto(
        val id: String,
        val title: String?,
        val link: String,
        val guid: String?,
        val author: String?,
        val publishedAt: String?,
        val descriptionHtml: String?,
        val categories: List<String>,
        val hubs: List<String>,
        val tags: List<String>,
    )

    private data class RssMetadata(
        val hubs: List<String>,
        val tags: List<String>,
    )

    private fun String.isHabrArticleUrl(): Boolean = extractHabrArticleId() != null

    private fun String.extractHabrArticleId(): String? {
        val regex = Regex("""/articles/(\d+)/?""")
        val match = regex.find(this)
        return match?.groupValues?.getOrNull(1)?.let { "habr-$it" }
    }

    /**
     * Parses RFC 822 / RFC 1123 date string to epoch milliseconds.
     * Uses kotlinx-datetime for KMP compatibility.
     * Examples: "Sat, 02 May 2026 10:00:00 GMT", "Wed, 02 Apr 2025 14:30:00 +0300"
     */
    private fun String.parseRfc822Date(): Long? {
        return try {
            val patterns = listOf(
                Regex("""(\d{1,2})\s+(\w{3})\s+(\d{4})\s+(\d{1,2}):(\d{2}):(\d{2})\s*(\w+)?"""),
                Regex("""(\d{1,2})\s+(\w{3})\s+(\d{4})\s+(\d{1,2}):(\d{2}):(\d{2})"""),
            )

            for (pattern in patterns) {
                val match = pattern.find(this)
                if (match != null) {
                    val groups = match.groupValues
                    val day = groups[1].toIntOrNull() ?: continue
                    val monthStr = groups[2]
                    val year = groups[3].toIntOrNull() ?: continue
                    val hour = groups[4].toIntOrNull() ?: 0
                    val minute = groups[5].toIntOrNull() ?: 0
                    val second = groups[6].toIntOrNull() ?: 0
                    val tz = groups.getOrNull(7) ?: "GMT"

                    val month = monthStr.toMonthNumber() ?: continue
                    val timeZone = tz.toTimeZone()

                    val localDateTime = kotlinx.datetime.LocalDateTime(
                        year = year,
                        month = Month(month),
                        day = day,
                        hour = hour,
                        minute = minute,
                        second = second,
                    )
                    return localDateTime.toInstant(timeZone).toEpochMilliseconds()
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun String.toMonthNumber(): Int? {
        return when (uppercase()) {
            "JAN" -> 1
            "FEB" -> 2
            "MAR" -> 3
            "APR" -> 4
            "MAY" -> 5
            "JUN" -> 6
            "JUL" -> 7
            "AUG" -> 8
            "SEP" -> 9
            "OCT" -> 10
            "NOV" -> 11
            "DEC" -> 12
            else -> null
        }
    }

    private fun String.toTimeZone(): kotlinx.datetime.TimeZone {
        return when (uppercase()) {
            "GMT", "UTC", "Z" -> kotlinx.datetime.TimeZone.UTC
            "EST" -> kotlinx.datetime.TimeZone.of("UTC-05:00")
            "EDT" -> kotlinx.datetime.TimeZone.of("UTC-04:00")
            "CST" -> kotlinx.datetime.TimeZone.of("UTC-06:00")
            "CDT" -> kotlinx.datetime.TimeZone.of("UTC-05:00")
            "MST" -> kotlinx.datetime.TimeZone.of("UTC-07:00")
            "MDT" -> kotlinx.datetime.TimeZone.of("UTC-06:00")
            "PST" -> kotlinx.datetime.TimeZone.of("UTC-08:00")
            "PDT" -> kotlinx.datetime.TimeZone.of("UTC-07:00")
            else -> kotlinx.datetime.TimeZone.UTC
        }
    }
}
