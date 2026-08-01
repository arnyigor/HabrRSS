package com.arny.habrrss

import com.arny.habrrss.data.rss.HabrRssSource
import com.arny.habrrss.domain.models.FeedKind
import com.arny.habrrss.domain.models.PageCursor
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HabrRssSourceTest {
    @Test
    fun exposesOnlyArticleFeeds() = runTest {
        val source = HabrRssSource(mockClient("<rss><channel/></rss>"))

        val feeds = source.getFeeds()

        assertEquals(1, feeds.size)
        assertTrue(feeds.any { it.kind == FeedKind.All && it.url.contains("/rss/articles/") })
        assertFalse(feeds.any { it.url.contains("/rss/posts/") || it.url.contains("/rss/news/") })
        assertTrue(feeds.all { it.kind == FeedKind.All })
        assertTrue(feeds.all { it.url.contains("with_hubs=true") && it.url.contains("with_tags=true") })
    }

    @Test
    fun ignoresRequestedPageBecauseHabrRssHasOnlyLatestLimit() = runTest {
        val urls = mutableListOf<String>()
        val source = HabrRssSource(mockClient("<rss><channel/></rss>", urls))

        source.getItems(HabrRssSource.FeedIds.All, page = PageCursor("2"))

        assertEquals(1, urls.size)
        assertFalse(urls.single().contains("page="))
    }

    @Test
    fun parsesRssItemsWithMetadataFromDescription() = runTest {
        val source = HabrRssSource(
            mockClient(
                """
                <rss>
                  <channel>
                    <item>
                      <title><![CDATA[Iceberg без Spark]]></title>
                      <link>https://habr.com/ru/articles/123/</link>
                      <guid>article-123</guid>
                      <pubDate>Sat, 02 May 2026 10:00:00 GMT</pubDate>
                      <author>shatzibitten</author>
                      <description><![CDATA[
                        <p>Краткое описание статьи.</p>
                        <img src="https://habrastorage.org/image.png" />
                        <p>Хабы: Базы данных, Big Data</p>
                        <p>Метки: Apache Doris, Iceberg</p>
                      ]]></description>
                    </item>
                  </channel>
                </rss>
                """.trimIndent(),
            ),
        )

        val page = source.getItems(HabrRssSource.FeedIds.All, page = null)
        val item = page.items.single()

        assertEquals("habr-123", item.id)
        assertEquals("Iceberg без Spark", item.title)
        assertEquals("shatzibitten", item.author?.displayName)
        assertEquals("https://habrastorage.org/image.png", item.imageUrl)
        assertEquals(listOf("Базы данных", "Big Data"), item.hubs.map { it.title })
        assertEquals(listOf("Apache Doris", "Iceberg"), item.tags.map { it.title })
        assertNotNull(page.updatedAt)
    }

    @Test
    fun filtersOnlyArticleUrlsAndNormalizesDuplicateCategories() = runTest {
        val source = HabrRssSource(
            mockClient(
                """
                <rss>
                  <channel>
                    <item>
                      <title><![CDATA[Про ML]]></title>
                      <link>https://habr.com/ru/articles/1065638/</link>
                      <guid isPermaLink="true">https://habr.com/ru/articles/1065638/</guid>
                      <description><![CDATA[<p>Описание</p>]]></description>
                      <category><![CDATA[ Машинное обучение ]]></category>
                      <category><![CDATA[Искусственный интеллект]]></category>
                      <category><![CDATA[ искусственный интеллект ]]></category>
                      <category><![CDATA[нейронные сети]]></category>
                    </item>
                    <item>
                      <title>Новость</title>
                      <link>https://habr.com/ru/news/1/</link>
                      <description>Не статья</description>
                    </item>
                    <item>
                      <title>Пост</title>
                      <link>https://habr.com/ru/posts/2/</link>
                      <description>Не статья</description>
                    </item>
                  </channel>
                </rss>
                """.trimIndent(),
            ),
        )

        val items = source.getItems(HabrRssSource.FeedIds.All, page = null).items

        assertEquals(listOf("habr-1065638"), items.map { it.id })
        assertEquals(
            listOf("Машинное обучение", "Искусственный интеллект", "нейронные сети"),
            items.single().hubs.map { it.title },
        )
        assertEquals(
            listOf("Машинное обучение", "Искусственный интеллект", "нейронные сети"),
            items.single().tags.map { it.title },
        )
    }

    private fun mockClient(body: String, urls: MutableList<String> = mutableListOf()): HttpClient = HttpClient(
        MockEngine { request ->
            urls += request.url.toString()
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/rss+xml"),
            )
        },
    )
}
