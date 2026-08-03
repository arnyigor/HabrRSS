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

    @Test
    fun enrichesApiArticleHubSlugsFromHtmlPage() = runTest {
        val urls = mutableListOf<String>()
        val source = HabrArticleContentSource(
            mockClient(urls) { url ->
                when {
                    url.contains("/kek/v2/articles/125") -> apiArticleJsonWithGeneratedHubAlias()
                    url.contains("/ru/articles/125/") -> MockBody(htmlArticleWithAndroidHub(), HttpStatusCode.OK, "text/html")
                    else -> error("Unexpected URL: $url")
                }
            },
        )

        val article = source.getArticleByUrl("https://habr.com/ru/articles/125/")
        val hub = article.hubs.single()

        assertEquals("Android", hub.title)
        assertEquals("android_dev", hub.slug)
        assertEquals(2, urls.size)
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

    private fun apiArticleJsonWithGeneratedHubAlias(): MockBody = MockBody(
        content =
            """
            {
              "id": "125",
              "timePublished": "2026-08-03T10:15:00+03:00",
              "titleHtml": "Android article",
              "leadData": { "textHtml": "<p>Preview</p>" },
              "textHtml": "<p>API full text</p><p>Second paragraph</p>",
              "hubs": [
                { "id": "hub-760110735", "alias": "hub-760110735", "title": "Android" }
              ]
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

    private fun htmlArticleWithAndroidHub(): String =
        """
        <html>
          <body>
            <a href="/ru/hubs/android_dev/">Android</a>
            <article class="tm-article-presenter__content">
              <h1>Android article</h1>
              <div id="post-content-body">
                <div class="article-formatted-body">
                  <p>Long HTML article body for slug enrichment from Habr page. This text is intentionally long enough for the extractor body length validation to pass without falling back.</p>
                  <p>Another long paragraph that keeps the sample realistic and verifies that hub links from the page are extracted together with article content.</p>
                  <p>Final long paragraph with enough words to pass the minimum full article length check in the extractor and return a parsed article.</p>
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
