package com.arny.habrrss

import com.arny.habrrss.core.network.createHttpClient
import com.arny.habrrss.data.article.HabrArticleContentSource
import com.arny.habrrss.data.rss.HabrRssSource
import com.arny.habrrss.domain.models.ArticleBlock
import com.arny.habrrss.ui.article.plainText
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HabrLiveIntegrationTest {
    @Test
    fun loadsFullArticleFromRealHabrPage() = runTest {
        val client = createHttpClient()
        try {
            val source = HabrRssSource(client)
            val page = source.getItems(HabrRssSource.FeedIds.All, page = null)
            val item = page.items.first { it.url.contains("/articles/") }

            assertTrue(item.tags.isNotEmpty(), "Expected Habr RSS tags to be parsed from item description")
            val article = HabrArticleContentSource(client).getArticleByUrl(item.url)
            val articleTextLength = article.blocks.sumOf { it.textLength() }

            assertEquals("Полная статья загружена с Habr.", article.sourceNotice)
            assertTrue(article.blocks.size > 3, "Expected full article blocks, got ${article.blocks.size}")
            assertTrue(articleTextLength > item.summary.length, "Expected full article text to be longer than RSS summary")
            assertTrue(articleTextLength > 500, "Expected full article text, got only $articleTextLength chars")
        } finally {
            client.close()
        }
    }
}

private fun ArticleBlock.textLength(): Int = when (this) {
    is ArticleBlock.CodeBlock -> code.length
    is ArticleBlock.Heading -> inline.plainText().length
    is ArticleBlock.Image -> alt?.length ?: 0
    is ArticleBlock.ListBlock -> items.flatten().sumOf { it.textLength() }
    is ArticleBlock.Paragraph -> inline.plainText().length
    is ArticleBlock.Quote -> blocks.sumOf { it.textLength() }
    is ArticleBlock.Spoiler -> blocks.sumOf { it.textLength() }
    is ArticleBlock.TableBlock -> rows.flatten().flatten().sumOf { it.textLength() }
    is ArticleBlock.UnknownHtml -> html.length
}
