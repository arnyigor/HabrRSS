package com.arny.habrrss

import com.arny.habrrss.data.remote.habr.dto.HabrArticleDto
import com.arny.habrrss.data.remote.habr.dto.HabrArticlesPageDto
import com.arny.habrrss.data.remote.habr.dto.HabrErrorDto
import com.arny.habrrss.data.remote.habr.habrJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HabrKekFixtureContractTest {
    @Test
    fun decodesHubPageFixture() {
        val dto = habrJson.decodeFromString<HabrArticlesPageDto>(fixture("habr/articles_page_hub.json"))

        assertTrue(dto.pagesCount > 0)
        assertTrue(dto.publicationIds.isNotEmpty())
        assertNotNull(dto.publicationRefs[dto.publicationIds.first()])
    }

    @Test
    fun decodesArticleDetailsFixture() {
        val dto = habrJson.decodeFromString<HabrArticleDto>(fixture("habr/article_details.json"))

        assertEquals("815309", dto.id)
        assertTrue(dto.textHtml.orEmpty().isNotBlank())
        assertNotNull(dto.author)
    }

    @Test
    fun decodesValidationErrorFixture() {
        val dto = habrJson.decodeFromString<HabrErrorDto>(fixture("habr/error_422.json"))

        assertEquals(422, dto.httpCode)
        assertEquals("validation_error", dto.errorCode)
    }

    private fun fixture(path: String): String =
        requireNotNull(javaClass.classLoader.getResource(path)) { "Missing fixture: $path" }.readText()
}
