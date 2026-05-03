package com.arny.habrrss

import com.arny.habrrss.data.api.HabrApiSource
import com.arny.habrrss.data.article.HabrWebReaderSource
import com.arny.habrrss.data.rss.GenericRssSource
import com.arny.habrrss.domain.models.FeedDescriptor
import com.arny.habrrss.domain.models.FeedKind
import com.arny.habrrss.domain.source.SourceUnavailableException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
    fun habrApiSourceIsExplicitlyUnavailableForMvp() = runTest {
        val source = HabrApiSource()

        assertTrue(source.getFeeds().isEmpty())
        assertTrue(source.getComments("article").isEmpty())
        assertFailsWith<SourceUnavailableException> {
            source.getItems("any", page = null)
        }
    }

    @Test
    fun webReaderFallbackBuildsMinimalArticleContent() = runTest {
        val article = HabrWebReaderSource().getArticleByUrl("https://habr.com/ru/articles/42/")

        assertEquals("https://habr.com/ru/articles/42/", article.id)
        assertEquals("Open original article", article.title)
        assertTrue(article.blocks.isNotEmpty())
        assertTrue(article.sourceNotice.contains("fallback"))
    }
}
