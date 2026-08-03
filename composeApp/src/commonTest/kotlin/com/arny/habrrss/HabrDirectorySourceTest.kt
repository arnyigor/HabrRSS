package com.arny.habrrss

import com.arny.habrrss.data.remote.habr.HabrApiClient
import com.arny.habrrss.data.remote.habr.HabrDirectorySource
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

class HabrDirectorySourceTest {
    @Test
    fun loadsHubsRawPageAndExposesRootKeys() = runTest {
        val urls = mutableListOf<String>()
        val source = HabrDirectorySource(HabrApiClient(mockClient(urls)))

        val page = source.getHubsPageRaw(page = 2)

        assertEquals(listOf("hubIds", "hubRefs", "pagesCount"), page.rootKeys)
        assertTrue(page.payload.containsKey("hubRefs"))
        assertTrue(urls.single().contains("/kek/v2/hubs/"))
        assertTrue(urls.single().contains("page=2"))
    }

    @Test
    fun loadsCompaniesRawPageAndExposesRootKeys() = runTest {
        val urls = mutableListOf<String>()
        val source = HabrDirectorySource(HabrApiClient(mockClient(urls)))

        val page = source.getCompaniesPageRaw(page = 1)

        assertEquals(listOf("companyIds", "companyRefs", "pagesCount"), page.rootKeys)
        assertTrue(page.payload.containsKey("companyRefs"))
        assertTrue(urls.single().contains("/kek/v2/companies/"))
        assertTrue(urls.single().contains("page=1"))
    }

    private fun mockClient(urls: MutableList<String>): HttpClient = HttpClient(
        MockEngine { request ->
            val url = request.url.toString()
            urls += url
            val body = if (url.contains("/hubs/")) {
                """{"pagesCount":11,"hubIds":["programming"],"hubRefs":{"programming":{}}}"""
            } else {
                """{"pagesCount":19,"companyIds":["habr"],"companyRefs":{"habr":{}}}"""
            }
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        },
    )
}
