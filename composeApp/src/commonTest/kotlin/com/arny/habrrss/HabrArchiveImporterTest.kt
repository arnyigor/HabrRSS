package com.arny.habrrss

import com.arny.habrrss.data.api.HabrApiSource
import com.arny.habrrss.data.database.InMemoryFeedDao
import com.arny.habrrss.data.remote.habr.HabrApiClient
import com.arny.habrrss.data.remote.habr.HabrPeriod
import com.arny.habrrss.data.sync.HabrArchiveImporter
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HabrArchiveImporterTest {
    @Test
    fun importsHubAllTimeSequentiallyAndStoresCompletedState() = runTest {
        val urls = mutableListOf<String>()
        val dao = InMemoryFeedDao()
        val importer = HabrArchiveImporter(
            api = HabrApiClient(mockClient(urls)),
            feedDao = dao,
            pageDelayMillis = 0,
        )

        importer.importHub("programming")

        val sourceKey = HabrApiSource.FeedIds.hub("programming", HabrPeriod.AllTime)
        val state = dao.getSyncState(sourceKey)
        val items = dao.getByFeedOnce(sourceKey)

        assertNotNull(state)
        assertEquals(HabrArchiveImporter.STATUS_COMPLETED, state.status)
        assertEquals(2, state.pagesProcessed)
        assertEquals(3, state.receivedCount)
        assertEquals(3, state.uniqueCount)
        assertEquals(setOf("habr-1", "habr-2", "habr-3", "habr-100"), items.map { it.id }.toSet())
        assertEquals(3, urls.size)
        assertTrue(urls[0].contains("period=alltime") && urls[0].contains("page=1"))
        assertTrue(urls[1].contains("period=alltime") && urls[1].contains("page=2"))
        assertTrue(urls[2].contains("period=weekly") && urls[2].contains("page=1"))
    }

    @Test
    fun pausedImportDoesNotStartAgainUntilStatusChanges() = runTest {
        val dao = InMemoryFeedDao()
        val sourceKey = HabrApiSource.FeedIds.hub("programming", HabrPeriod.AllTime)
        val importer = HabrArchiveImporter(
            api = HabrApiClient(mockClient(mutableListOf())),
            feedDao = dao,
            pageDelayMillis = 0,
        )

        importer.importHub("programming")
        importer.pauseImport(sourceKey)

        val state = dao.getSyncState(sourceKey)

        assertEquals(HabrArchiveImporter.STATUS_PAUSED, state?.status)
    }

    private fun mockClient(urls: MutableList<String>): HttpClient = HttpClient(
        MockEngine { request ->
            val url = request.url.toString()
            urls += url
            val body = when {
                url.contains("period=weekly") -> pageJson(pagesCount = 1, ids = listOf("100"))
                url.contains("page=2") -> pageJson(pagesCount = 2, ids = listOf("3"))
                else -> pageJson(pagesCount = 2, ids = listOf("1", "2"))
            }
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        },
    )

    private fun pageJson(pagesCount: Int, ids: List<String>): String {
        val refs = ids.joinToString(",") { id ->
            """"$id":{"id":"$id","timePublished":"2026-08-03T10:15:00+03:00","titleHtml":"Article $id","leadData":{"textHtml":"<p>Preview $id</p>"}}"""
        }
        return """{"pagesCount":$pagesCount,"publicationIds":[${ids.joinToString(",") { "\"$it\"" }}],"publicationRefs":{$refs}}"""
    }
}
