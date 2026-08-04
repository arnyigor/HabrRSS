package com.arny.habrrss

import com.arny.habrrss.data.rss.HtmlArticleParser
import com.arny.habrrss.domain.models.ArticleBlock
import com.arny.habrrss.domain.models.InlineNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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

    @Test
    fun parsesCodeImagesOrderedListsAndIgnoresNoise() {
        val blocks = HtmlArticleParser.parse(
            """
            <script>alert(1)</script>
            <pre><code class="language-kotlin">fun main() = Unit</code></pre>
            <img src="//habrastorage.org/image.png" alt="diagram">
            <ol>
                <li><strong>Step</strong> one</li>
                <li><code>StepTwo()</code></li>
            </ol>
            """.trimIndent(),
            baseUrl = "https://habr.com/ru/articles/42/",
        )

        val code = blocks[0] as ArticleBlock.CodeBlock
        val image = blocks[1] as ArticleBlock.Image
        val list = blocks[2] as ArticleBlock.ListBlock

        assertEquals("kotlin", code.language)
        assertEquals("fun main() = Unit", code.code)
        assertEquals("https://habrastorage.org/image.png", image.url)
        assertEquals("diagram", image.alt)
        assertTrue(list.ordered)
        assertEquals("Step one", (list.items.first().single() as ArticleBlock.Paragraph).inline.plain())
    }

    @Test
    fun parsesPlainTextAsParagraphWhenThereAreNoElements() {
        val blocks = HtmlArticleParser.parse("plain text only")

        assertEquals("plain text only", (blocks.single() as ArticleBlock.Paragraph).inline.plain())
    }

    @Test
    fun parsesDetailsAsSpoilerWithoutDuplicatingSummary() {
        val blocks = HtmlArticleParser.parse(
            """
            <details>
                <summary>Show setup</summary>
                <p>Hidden paragraph</p>
                <pre><code class="language-kotlin">val answer = 42</code></pre>
            </details>
            """.trimIndent(),
        )

        val spoiler = blocks.single() as ArticleBlock.Spoiler

        assertEquals("Show setup", spoiler.title)
        assertEquals(2, spoiler.blocks.size)
        assertEquals("Hidden paragraph", (spoiler.blocks.first() as ArticleBlock.Paragraph).inline.plain())
        assertEquals("val answer = 42", (spoiler.blocks.last() as ArticleBlock.CodeBlock).code)
    }

    @Test
    fun preservesLineBreaksInsideInlineContent() {
        val blocks = HtmlArticleParser.parse("""<p>First<br>Second</p>""")

        val paragraph = blocks.single() as ArticleBlock.Paragraph

        assertEquals("First\nSecond", paragraph.inline.plain())
    }

    @Test
    fun preservesNestedListsInsideListItems() {
        val blocks = HtmlArticleParser.parse(
            """
            <ul>
                <li>Parent
                    <ul>
                        <li>Child</li>
                    </ul>
                </li>
            </ul>
            """.trimIndent(),
        )

        val list = blocks.single() as ArticleBlock.ListBlock
        val item = list.items.single()

        assertEquals("Parent", (item[0] as ArticleBlock.Paragraph).inline.plain().trim())
        assertTrue(item[1] is ArticleBlock.ListBlock)
        assertEquals("Child", (((item[1] as ArticleBlock.ListBlock).items.single().single()) as ArticleBlock.Paragraph).inline.plain())
    }

    @Test
    fun extractsLazyImageUrls() {
        val blocks = HtmlArticleParser.parse(
            """<figure><img data-src="/images/pic.png" alt="lazy"></figure>""",
            baseUrl = "https://habr.com/ru/articles/42/",
        )

        val image = blocks.single() as ArticleBlock.Image

        assertEquals("https://habr.com/images/pic.png", image.url)
        assertEquals("lazy", image.alt)
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
