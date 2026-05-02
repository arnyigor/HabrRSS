package com.arny.habrrss

import com.arny.habrrss.domain.export.MarkdownExporter
import com.arny.habrrss.domain.models.ArticleBlock
import com.arny.habrrss.domain.models.ArticleContent
import com.arny.habrrss.domain.models.InlineNode
import kotlin.test.Test
import kotlin.test.assertContains

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
}
