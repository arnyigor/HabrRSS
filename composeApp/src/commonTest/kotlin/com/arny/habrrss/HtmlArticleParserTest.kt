package com.arny.habrrss

import com.arny.habrrss.data.rss.HtmlArticleParser
import com.arny.habrrss.domain.models.ArticleBlock
import com.arny.habrrss.domain.models.InlineNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class HtmlArticleParserTest {
    @Test
    fun parsesNestedArticleBodyWithoutDroppingBlocks() {
        val blocks = HtmlArticleParser.parse(
            """
            <div>
                <h2>Intro</h2>
                <p>First paragraph</p>
                <p>Second paragraph</p>
            </div>
            """.trimIndent(),
        )

        assertEquals(3, blocks.size)
        assertIs<ArticleBlock.Heading>(blocks[0])
        assertEquals("First paragraph", (blocks[1] as ArticleBlock.Paragraph).inline.plain())
        assertEquals("Second paragraph", (blocks[2] as ArticleBlock.Paragraph).inline.plain())
    }

    @Test
    fun normalizesRelativeLinksAgainstArticleOrigin() {
        val blocks = HtmlArticleParser.parse(
            """<p><a href="/ru/articles/1/">Read more</a></p>""",
            baseUrl = "https://habr.com/ru/articles/999/",
        )

        val paragraph = blocks.single() as ArticleBlock.Paragraph
        val link = paragraph.inline.single() as InlineNode.Link

        assertEquals("https://habr.com/ru/articles/1/", link.url)
    }

    @Test
    fun preservesLinksInsideTableOfContentsList() {
        val blocks = HtmlArticleParser.parse(
            """
            <ul>
                <li><a href="#intro">Вступление</a></li>
                <li><a href="#steps">Первые шаги</a></li>
            </ul>
            """.trimIndent(),
            baseUrl = "https://habr.com/ru/articles/42/",
        )

        val list = blocks.single() as ArticleBlock.ListBlock
        val firstParagraph = list.items.first().single() as ArticleBlock.Paragraph
        val firstLink = firstParagraph.inline.single() as InlineNode.Link

        assertEquals("Вступление", firstLink.text)
        assertEquals("https://habr.com/ru/articles/42/#intro", firstLink.url)
    }

    @Test
    fun preservesLinksInsideQuoteBlocks() {
        val blocks = HtmlArticleParser.parse(
            """<blockquote><p><a href="https://plugins.jetbrains.com/">Ссылка</a></p></blockquote>""",
            baseUrl = "https://habr.com/ru/articles/42/",
        )

        val quote = blocks.single() as ArticleBlock.Quote
        val paragraph = quote.blocks.single() as ArticleBlock.Paragraph
        val link = paragraph.inline.single() as InlineNode.Link

        assertEquals("Ссылка", link.text)
        assertEquals("https://plugins.jetbrains.com/", link.url)
    }
}

private fun List<InlineNode>.plain(): String = joinToString("") { node ->
    when (node) {
        is InlineNode.Bold -> node.children.plain()
        is InlineNode.Code -> node.value
        is InlineNode.Italic -> node.children.plain()
        is InlineNode.Link -> node.text
        is InlineNode.Text -> node.value
    }
}
