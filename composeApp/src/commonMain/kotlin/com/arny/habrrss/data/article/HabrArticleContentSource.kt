package com.arny.habrrss.data.article

import com.arny.habrrss.data.rss.HtmlArticleParser
import com.arny.habrrss.domain.models.ArticleBlock
import com.arny.habrrss.domain.models.ArticleContent
import com.arny.habrrss.domain.models.Author
import com.arny.habrrss.domain.models.CommentNode
import com.arny.habrrss.domain.source.ArticleCommentsSource
import com.arny.habrrss.domain.source.ArticleContentSource
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText

/**
 * Article content source that fetches full article HTML from Habr.
 * Separated from FeedSource to clarify responsibilities:
 * - FeedSource: handles RSS feed listing
 * - This: handles full article content loading
 */
class HabrArticleContentSource(
    private val client: HttpClient,
    private val extractor: HabrArticleContentExtractor = HabrArticleContentExtractor(),
) : ArticleContentSource, ArticleCommentsSource {

    override suspend fun getArticleByUrl(url: String): ArticleContent {
        val normalizedUrl = normalizeArticleUrl(url)
        val html = client.get(normalizedUrl).bodyAsText()
        return extractor.extract(articleId = extractArticleId(normalizedUrl), articleUrl = normalizedUrl, html = html)
    }

    override suspend fun getCommentsByUrl(url: String): List<CommentNode> {
        val normalizedUrl = normalizeArticleUrl(url)
        val html = client.get(normalizedUrl).bodyAsText()
        val doc = Ksoup.parse(html)
        return doc.select(CommentSelectors)
            .mapNotNull { element -> element.toCommentNode(normalizedUrl) }
            .distinctBy { it.id }
            .take(MaxComments)
    }

    private fun Element.toCommentNode(baseUrl: String): CommentNode? {
        val body = selectFirst(".tm-comment__body-content, .comment__message, .tm-comment__body") ?: return null
        body.select("script, style, noscript, svg, iframe").remove()
        val html = body.html().takeIf { it.isNotBlank() } ?: return null
        val blocks = HtmlArticleParser.parse(html, baseUrl)
            .ifEmpty { listOf(ArticleBlock.Paragraph(emptyList())) }
        val authorName = selectFirst(".tm-user-info__username, .tm-comment__user-info a, a[href*=/users/]")
            ?.text()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        val id = attr("id").takeIf { it.isNotBlank() }
            ?: attr("data-id").takeIf { it.isNotBlank() }
            ?: "comment-${html.hashCode()}"
        return CommentNode(
            id = id,
            author = authorName?.let { Author(id = "author-${it.hashCode()}", displayName = it, profileUrl = null) },
            publishedAt = selectFirst("time")?.attr("datetime")?.takeIf { it.isNotBlank() }
                ?: selectFirst("time")?.text()?.trim()?.takeIf { it.isNotBlank() },
            body = blocks,
            children = emptyList(),
        )
    }

    private fun normalizeArticleUrl(url: String): String {
        val value = url.replace("&amp;", "&").trim()
        return when {
            value.startsWith("http://") || value.startsWith("https://") -> value
            value.all { it.isDigit() } -> "https://habr.com/ru/articles/$value/"
            else -> value
        }
    }

    private fun extractArticleId(url: String): String {
        // Extract article ID from URL like https://habr.com/ru/articles/123456/
        val regex = """/articles/(\d+)/?""".toRegex()
        return regex.find(url)?.groupValues?.getOrNull(1) ?: url
    }

    private companion object {
        const val CommentSelectors = ".tm-comment-thread__comment, .tm-comment, [id^=comment_], [data-test-id=comment]"
        const val MaxComments = 100
    }
}
