package com.arny.habrrss

import com.arny.habrrss.data.article.HabrArticleContentSource
import com.arny.habrrss.domain.models.ArticleBlock
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HabrArticleContentSourceTest {
    @Test
    fun loadsArticleDetailsFromKekApiBeforeHtmlFallback() = runTest {
        val urls = mutableListOf<String>()
        val source = HabrArticleContentSource(
            mockClient(urls) { url ->
                when {
                    url.contains("/kek/v2/articles/123") -> apiArticleJson("API full text")
                    else -> error("Unexpected URL: $url")
                }
            },
        )

        val article = source.getArticleByUrl("https://habr.com/ru/articles/123/")

        assertEquals("habr-123", article.id)
        assertEquals("Заголовок из API", article.title)
        assertEquals("Полная статья загружена с Habr.", article.sourceNotice)
        assertTrue(article.blocks.any { it is ArticleBlock.Paragraph })
        assertEquals(1, urls.size)
        assertTrue(urls.single().contains("/kek/v2/articles/123"))
    }

    @Test
    fun fallsBackToHtmlPageWhenKekApiFails() = runTest {
        val urls = mutableListOf<String>()
        val source = HabrArticleContentSource(
            mockClient(urls) { url ->
                when {
                    url.contains("/kek/v2/articles/124") -> MockBody("{}", HttpStatusCode.InternalServerError)
                    url.contains("/ru/articles/124/") -> MockBody(htmlArticle(), HttpStatusCode.OK, "text/html")
                    else -> error("Unexpected URL: $url")
                }
            },
        )

        val article = source.getArticleByUrl("https://habr.com/ru/articles/124/")

        assertEquals("HTML full title", article.title)
        assertEquals("Полная статья загружена с Habr.", article.sourceNotice)
        assertEquals(2, urls.size)
        assertTrue(urls.first().contains("/kek/v2/articles/124"))
        assertTrue(urls.last().contains("/ru/articles/124/"))
    }

    private fun mockClient(
        urls: MutableList<String>,
        responseForUrl: (String) -> MockBody,
    ): HttpClient = HttpClient(
        MockEngine { request ->
            val url = request.url.toString()
            urls += url
            val body = responseForUrl(url)
            respond(
                content = body.content,
                status = body.status,
                headers = headersOf(HttpHeaders.ContentType, body.contentType),
            )
        },
    )

    private fun apiArticleJson(text: String): MockBody = MockBody(
        content =
            """
            {
              "id": "123",
              "timePublished": "2026-08-03T10:15:00+03:00",
              "titleHtml": "<b>Заголовок из API</b>",
              "leadData": { "textHtml": "<p>Preview</p>" },
              "textHtml": "<p>$text</p><p>Second paragraph</p>"
            }
            """.trimIndent(),
    )

    private fun htmlArticle(): String =
        """
        <html>
          <body>
            <article class="tm-article-presenter__content">
              <h1>HTML full title</h1>
              <div id="post-content-body">
                <div class="article-formatted-body">
                  <p>Первый длинный абзац fallback HTML статьи, который достаточно большой для extractor и проверки.</p>
                  <p>Второй длинный абзац fallback HTML статьи, чтобы body не считался слишком коротким.</p>
                  <p>Третий длинный абзац fallback HTML статьи, завершающий проверочный пример.</p>
                </div>
              </div>
            </article>
          </body>
        </html>
        """.trimIndent()

    private data class MockBody(
        val content: String,
        val status: HttpStatusCode = HttpStatusCode.OK,
        val contentType: String = "application/json",
    )
}
