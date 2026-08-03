package com.arny.habrrss

import com.arny.habrrss.data.api.HabrApiSource
import com.arny.habrrss.data.article.HabrWebReaderSource
import com.arny.habrrss.data.rss.GenericRssSource
import com.arny.habrrss.domain.models.FeedDescriptor
import com.arny.habrrss.domain.models.FeedKind
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

class SourceAdaptersTest {
    @Test
    fun genericRssSourceExposesDescriptorsAndEmptyCachedPage() = runTest {
        val descriptor = FeedDescriptor(
            id = "custom",
            title = "Custom",
            sourceTitle = "RSS",
            url = "https://example.com/rss.xml",
            description = "Custom RSS",
            kind = FeedKind.Custom,
        )
        val source = GenericRssSource(listOf(descriptor))

        val page = source.getItems("custom", page = null)

        assertEquals(listOf(descriptor), source.getFeeds())
        assertTrue(page.items.isEmpty())
        assertTrue(page.fromCache)
    }

    @Test
    fun habrApiSourceExposesLatestFeedAndUsesRemotePage() = runTest {
        val source = HabrApiSource(mockJsonClient("""{"pagesCount":1,"publicationIds":[],"publicationRefs":{}}"""))

        assertEquals(1, source.getFeeds().size)
        assertEquals(HabrApiSource.FeedIds.All, source.getFeeds().single().id)
        assertTrue(source.getComments("article").isEmpty())
        assertTrue(source.getItems(HabrApiSource.FeedIds.All, page = null).items.isEmpty())
    }

    @Test
    fun webReaderFallbackBuildsMinimalArticleContent() = runTest {
        val article = HabrWebReaderSource().getArticleByUrl("https://habr.com/ru/articles/42/")

        assertEquals("https://habr.com/ru/articles/42/", article.id)
        assertEquals("Open original article", article.title)
        assertTrue(article.blocks.isNotEmpty())
        assertTrue(article.sourceNotice.contains("fallback"))
    }

    private fun mockJsonClient(body: String): HttpClient = HttpClient(
        MockEngine {
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        },
    )
}
