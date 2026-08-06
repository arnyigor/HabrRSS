package com.arny.habrrss

import com.arny.habrrss.data.api.HabrApiSource
import com.arny.habrrss.data.remote.habr.HabrApiClient
import com.arny.habrrss.data.remote.habr.HabrArticlesRequest
import com.arny.habrrss.data.remote.habr.HabrPeriod
import com.arny.habrrss.data.remote.habr.error.HabrRemoteException
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
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HabrApiSourceTest {
    @Test
    fun requestsLatestWithRequiredDatePeriodParameters() = runTest {
        val urls = mutableListOf<String>()
        val client = mockJsonClient(articlesPageJson(), urls)
        val api = HabrApiClient(client)

        api.getArticles(HabrArticlesRequest.Latest(page = 3, period = HabrPeriod.Weekly))

        val url = urls.single()
        assertTrue(url.startsWith("https://habr.com/kek/v2/articles/"))
        assertTrue(url.contains("sort=date"))
        assertTrue(url.contains("period=weekly"))
        assertTrue(url.contains("fl=ru"))
        assertTrue(url.contains("hl=ru"))
        assertTrue(url.contains("page=3"))
        assertTrue(url.contains("perPage=100"))
    }

    @Test
    fun requestsHubArchiveWithAllTimePeriod() = runTest {
        val urls = mutableListOf<String>()
        val source = HabrApiSource(mockJsonClient(articlesPageJson(), urls))

        source.getItems(HabrApiSource.FeedIds.hub("android_dev"), page = PageCursor("4"))

        val url = urls.single()
        assertTrue(url.contains("hub=android_dev"))
        assertTrue(url.contains("sort=date"))
        assertTrue(url.contains("period=alltime"))
        assertTrue(url.contains("page=4"))
        assertTrue(url.contains("perPage=100"))
    }

    @Test
    fun sourceSortsByNewestAndSkipsMissingRefs() = runTest {
        val source = HabrApiSource(mockJsonClient(articlesPageJson()))

        val page = source.getItems(HabrApiSource.FeedIds.All, page = null)

        assertEquals(listOf("habr-2", "habr-1"), page.items.map { it.id })
        assertEquals("Вторая статья", page.items.first().title)
        assertEquals("Превью", page.items.first().summary)
        assertEquals("author", page.items.first().author?.displayName)
        assertEquals(listOf("programming"), page.items.first().hubs.map { it.id })
        assertEquals("2", page.nextCursor?.value)
        assertNotNull(page.updatedAt)
    }

    @Test
    fun generatedHubAliasIsNotTreatedAsSlug() = runTest {
        val source = HabrApiSource(mockJsonClient(generatedHubAliasPageJson()))

        val page = source.getItems(HabrApiSource.FeedIds.All, page = null)
        val hub = page.items.single().hubs.single()

        assertEquals("hub-760110735", hub.id)
        assertEquals("Информационная безопасность", hub.title)
        assertNull(hub.slug)
    }

    @Test
    fun parsesFractionalImagePositionWithoutContractChange() = runTest {
        // Habr returns fractional image coordinates (positionY: 82.727272727273). They must not
        // abort the whole page decode with ContractChanged (regression for archive pagination).
        val source = HabrApiSource(
            mockJsonClient(
                """
                {
                  "pagesCount": 1,
                  "publicationIds": ["1"],
                  "publicationRefs": {
                    "1": {
                      "id": "1",
                      "timePublished": "2026-08-03T10:15:00+03:00",
                      "titleHtml": "Image",
                      "leadData": {
                        "textHtml": "<p>Preview</p>",
                        "image": {
                          "url": "https://habrastorage.org/img.png",
                          "fit": "cover",
                          "positionX": 2,
                          "positionY": 82.727272727273
                        }
                      }
                    }
                  }
                }
                """.trimIndent()
            )
        )

        val page = source.getItems(HabrApiSource.FeedIds.All, page = null)

        assertEquals(listOf("habr-1"), page.items.map { it.id })
        assertNull(page.nextCursor)
    }

    @Test
    fun mapsValidationStatusToTypedException() = runTest {
        val client = mockJsonClient(
            body = """{"httpCode":422,"message":"Form errors"}""",
            status = HttpStatusCode.UnprocessableEntity,
        )
        val api = HabrApiClient(client)

        assertFailsWith<HabrRemoteException.Validation> {
            api.getArticles(HabrArticlesRequest.Latest())
        }
    }

    private fun mockJsonClient(
        body: String,
        urls: MutableList<String> = mutableListOf(),
        status: HttpStatusCode = HttpStatusCode.OK,
    ): HttpClient = HttpClient(
        MockEngine { request ->
            urls += request.url.toString()
            respond(
                content = body,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        },
    )

    private fun articlesPageJson(): String =
        """
        {
          "pagesCount": 2,
          "publicationIds": ["1", "2", "missing"],
          "publicationRefs": {
            "1": {
              "id": "1",
              "timePublished": "2026-08-02T10:15:00+03:00",
              "titleHtml": "<b>Первая статья</b>",
              "leadData": { "textHtml": "<p>Первое превью</p>" }
            },
            "2": {
              "id": "2",
              "timePublished": "2026-08-03T10:15:00+03:00",
              "titleHtml": "<b>Вторая статья</b>",
              "leadData": {
                "textHtml": "<p>Превью</p>",
                "imageUrl": "https://habrastorage.org/image.png"
              },
              "author": { "alias": "author" },
              "statistics": { "commentsCount": 7, "score": 5 },
              "hubs": [
                { "id": "hub-1", "alias": "programming", "title": "Программирование" }
              ]
            }
          }
        }
        """.trimIndent()

    private fun generatedHubAliasPageJson(): String =
        """
        {
          "pagesCount": 1,
          "publicationIds": ["1"],
          "publicationRefs": {
            "1": {
              "id": "1",
              "timePublished": "2026-08-03T10:15:00+03:00",
              "titleHtml": "Security",
              "leadData": { "textHtml": "<p>Preview</p>" },
              "hubs": [
                { "id": "hub-760110735", "alias": "hub-760110735", "title": "Информационная безопасность" }
              ]
            }
          }
        }
        """.trimIndent()
}
