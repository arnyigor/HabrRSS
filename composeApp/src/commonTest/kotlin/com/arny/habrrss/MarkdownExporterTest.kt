package com.arny.habrrss

import com.arny.habrrss.domain.export.MarkdownExporter
import com.arny.habrrss.domain.models.ArticleBlock
import com.arny.habrrss.domain.models.ArticleContent
import com.arny.habrrss.domain.models.InlineNode
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class MarkdownExporterTest {
    @Test
    fun exportsArticleTitleOriginalLinkAndCodeBlock() {
        val article = ArticleContent(
            id = "article-1",
            title = "Reader architecture",
            url = "https://habr.com/ru/articles/example/",
            imageUrl = "https://example.com/image.jpg",
            author = null,
            publishedAt = null,
            tags = emptyList(),
            hubs = emptyList(),
            sourceNotice = "test",
            blocks = listOf(
                ArticleBlock.Paragraph(listOf(InlineNode.Text("RSS-first text"))),
                ArticleBlock.CodeBlock(language = "kotlin", code = "val source: FeedSource"),
            ),
        )

        val markdown = MarkdownExporter().export(article)

        assertContains(markdown, "# Reader architecture")
        assertContains(markdown, "Original: https://habr.com/ru/articles/example/")
        assertContains(markdown, "```kotlin")
        assertContains(markdown, "val source: FeedSource")
    }

    @Test
    fun exportsInlineFormattingListsQuotesImagesAndUnknownHtml() {
        val article = ArticleContent(
            id = "article-2",
            title = "Formatting",
            url = "https://example.com/article",
            imageUrl = null,
            author = null,
            publishedAt = null,
            tags = emptyList(),
            hubs = emptyList(),
            sourceNotice = "test",
            blocks = listOf(
                ArticleBlock.Heading(2, listOf(InlineNode.Text("Section"))),
                ArticleBlock.Paragraph(
                    listOf(
                        InlineNode.Bold(listOf(InlineNode.Text("bold"))),
                        InlineNode.Text(" and "),
                        InlineNode.Italic(listOf(InlineNode.Text("italic"))),
                        InlineNode.Text(" with "),
                        InlineNode.Code("code"),
                        InlineNode.Text(" and "),
                        InlineNode.Link("link", "https://example.com"),
                    ),
                ),
                ArticleBlock.Image("https://example.com/image.png", "image"),
                ArticleBlock.ListBlock(
                    ordered = true,
                    items = listOf(
                        listOf(ArticleBlock.Paragraph(listOf(InlineNode.Text("first")))),
                        listOf(ArticleBlock.Paragraph(listOf(InlineNode.Text("second")))),
                    ),
                ),
                ArticleBlock.Quote(listOf(ArticleBlock.Paragraph(listOf(InlineNode.Text("quoted"))))),
                ArticleBlock.Spoiler(
                    title = "Details",
                    blocks = listOf(ArticleBlock.Paragraph(listOf(InlineNode.Text("hidden")))),
                ),
                ArticleBlock.UnknownHtml("<details>raw</details>"),
            ),
        )

        val markdown = MarkdownExporter().export(article)

        assertContains(markdown, "## Section")
        assertContains(markdown, "**bold** and _italic_ with `code` and [link](https://example.com)")
        assertContains(markdown, "![image](https://example.com/image.png)")
        assertContains(markdown, "1. first")
        assertContains(markdown, "2. second")
        assertContains(markdown, "> quoted")
        assertContains(markdown, "<summary>Details</summary>")
        assertContains(markdown, "hidden")
        assertContains(markdown, "<details>raw</details>")
    }

    @Test
    fun trimsTrailingBlankLines() {
        val markdown = MarkdownExporter().export(
            ArticleContent(
                id = "empty",
                title = "Empty",
                url = "https://example.com",
                imageUrl = null,
                author = null,
                publishedAt = null,
                tags = emptyList(),
                hubs = emptyList(),
                blocks = emptyList(),
                sourceNotice = "test",
            ),
        )

        assertEquals(markdown.trimEnd(), markdown)
    }
}
