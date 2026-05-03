package com.arny.habrrss.data.article

import com.arny.habrrss.data.article.HabrArticleContentExtractor
import com.arny.habrrss.domain.models.ArticleContent
import com.arny.habrrss.domain.source.ArticleContentSource
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
) : ArticleContentSource {

    override suspend fun getArticleByUrl(url: String): ArticleContent {
        val normalizedUrl = normalizeArticleUrl(url)
        val html = client.get(normalizedUrl).bodyAsText()
        return extractor.extract(articleId = extractArticleId(normalizedUrl), articleUrl = normalizedUrl, html = html)
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
}