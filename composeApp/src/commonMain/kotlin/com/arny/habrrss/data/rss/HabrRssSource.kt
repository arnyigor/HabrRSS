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
import com.arny.habrrss.domain.models.CursorDirection
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
            description = "Все статьи Хабра",
            kind = FeedKind.All
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

        // Determine page number from cursor or start from page 1
        val pageNumber = page?.value?.toIntOrNull() ?: 1

        // Build URL with page parameter
        val urlWithPage = buildUrlWithPage(descriptor.url, pageNumber)

        val xml = client.get(urlWithPage).bodyAsText()
        val doc = Ksoup.parse(xml, Parser.xmlParser())
        val items = doc.select("item").map { parseItem(it, feedId) }

        // If we got full page (100 items), there's likely more pages
        // If less, it's the last page
        val nextCursor = if (items.size >= 100) {
            PageCursor(value = "${pageNumber + 1}", direction = CursorDirection.Next)
        } else {
            null
        }

        return FeedPage(
            items = items,
            nextCursor = nextCursor,
            fromCache = false,
            updatedAt = "${Clock.System.now().toEpochMilliseconds()}"
        )
    }

    private fun buildUrlWithPage(baseUrl: String, page: Int): String {
        val separator = if (baseUrl.contains('?')) "&" else "?"
        return "$baseUrl${separator}page=$page"
    }

    private fun parseItem(element: Element, feedId: String): FeedItem {
        val title = element.rssText("title")?.takeIf { it.isNotBlank() }
            ?: "Без заголовка"
        val link = element.rssText("link") ?: ""
        // Stable ID priority: 1) article ID from URL, 2) canonical URL, 3) guid, 4) hash of title+pubDate+author
        val articleId = extractStableArticleId(link, element.rssText("guid"))
        val pubDate = element.rssText("pubDate")
        val pubDateEpoch = pubDate?.parseRfc822Date()
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
            id = articleId,
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
            publishedAtEpoch = pubDateEpoch,
            tags = tags,
            hubs = hubs,
            rating = null,
            commentsCount = null,
            isRead = false,
            isBookmarked = false
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
        const val Kotlin = "habr-kotlin"
    }

    private data class RssMetadata(
        val hubs: List<String>,
        val tags: List<String>,
    )

    /**
     * Extracts stable article ID with priority:
     * 1. Article ID from URL pattern /articles/{id}/
     * 2. Canonical URL (without query/fragment)
     * 3. GUID from RSS
     * 4. Hash of normalized title + pubDate + author (fallback)
     */
    private fun extractStableArticleId(link: String, guid: String?): String {
        // Try to extract article ID from URL like https://habr.com/ru/articles/123456/
        val articleIdFromUrl = link.extractHabrArticleId()
        if (articleIdFromUrl != null) {
            return articleIdFromUrl
        }

        // Try canonical URL (without query params and fragment)
        val canonicalUrl = link.split('?').firstOrNull()?.split('#')?.firstOrNull()
        if (!canonicalUrl.isNullOrBlank()) {
            return canonicalUrl
        }

        // Try GUID
        if (!guid.isNullOrBlank()) {
            return guid
        }

        // Fallback: hash of link (should never happen for valid Habr RSS)
        return "habr-${link.hashCode()}"
    }

    private fun String.extractHabrArticleId(): String? {
        // Pattern: /articles/123456/ or /ru/articles/123456/
        val regex = Regex("/(?:ru|en)/articles/(\\d+)/")
        val match = regex.find(this)
        return match?.groupValues?.get(1)?.let { "habr-$it" }
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
