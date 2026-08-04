package com.arny.habrrss

import com.arny.habrrss.domain.models.ArticleBlock
import com.arny.habrrss.domain.models.ArticleContent
import com.arny.habrrss.domain.models.Author
import com.arny.habrrss.domain.models.FeedSettings
import com.arny.habrrss.domain.models.Hub
import com.arny.habrrss.domain.models.InlineNode
import com.arny.habrrss.domain.models.Tag
import com.arny.habrrss.ui.article.LINK_TAG
import com.arny.habrrss.ui.article.habrArticleIdFromUrl
import com.arny.habrrss.ui.article.markdownText
import com.arny.habrrss.ui.article.normalizedExternalUrl
import com.arny.habrrss.ui.article.plainText
import com.arny.habrrss.ui.article.shareText
import com.arny.habrrss.ui.article.toAnnotatedString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArticleScreenTest {
    @Test
    fun toAnnotatedString_convertsTextNodes() {
        val nodes = listOf(
            InlineNode.Text("Hello "),
            InlineNode.Text("World"),
        )
        val result = nodes.toAnnotatedString()
        assertEquals("Hello World", result.toString())
    }

    @Test
    fun toAnnotatedString_convertsBoldNodes() {
        val nodes = listOf(
            InlineNode.Bold(listOf(InlineNode.Text("bold"))),
        )
        val result = nodes.toAnnotatedString()
        assertTrue(result.toString().contains("bold"))
    }

    @Test
    fun toAnnotatedString_convertsItalicNodes() {
        val nodes = listOf(
            InlineNode.Italic(listOf(InlineNode.Text("italic"))),
        )
        val result = nodes.toAnnotatedString()
        assertTrue(result.toString().contains("italic"))
    }

    @Test
    fun toAnnotatedString_convertsCodeNodes() {
        val nodes = listOf(
            InlineNode.Code("code"),
        )
        val result = nodes.toAnnotatedString()
        assertTrue(result.toString().contains("code"))
    }

    @Test
    fun toAnnotatedString_convertsLinkNodes() {
        val nodes = listOf(
            InlineNode.Link(text = "link", url = "https://example.com/article?a=1&amp;b=2"),
        )
        val result = nodes.toAnnotatedString()
        assertTrue(result.toString().contains("link"))
        // Check that URL annotation is present
        val annotations = result.getStringAnnotations(LINK_TAG, 0, result.length)
        assertTrue(annotations.any { it.item == "https://example.com/article?a=1&b=2" })
    }

    @Test
    fun plainText_convertsAllNodesToString() {
        val nodes = listOf(
            InlineNode.Bold(listOf(InlineNode.Text("bold"))),
            InlineNode.Text(" "),
            InlineNode.Italic(listOf(InlineNode.Text("italic"))),
            InlineNode.Code("code"),
            InlineNode.Link(text = "link", url = "https://example.com"),
        )
        val result = nodes.plainText()
        assertEquals("bold italiccodelink", result)
    }

    @Test
    fun articleContent_hasCorrectStructure() {
        val article = ArticleContent(
            id = "test-article",
            title = "Test Article",
            url = "https://example.com/article",
            imageUrl = "https://example.com/image.jpg",
            author = Author("author-1", "Test Author", "https://example.com/author"),
            publishedAt = "2026-05-02",
            tags = listOf(Tag("tag1", "Tag 1")),
            hubs = listOf(Hub("hub1", "Hub 1")),
            blocks = listOf(
                ArticleBlock.Heading(1, listOf(InlineNode.Text("Introduction"))),
                ArticleBlock.Paragraph(listOf(InlineNode.Text("This is a paragraph."))),
                ArticleBlock.CodeBlock("kotlin", "fun main() { println(\"Hello\") }"),
                ArticleBlock.Image("https://example.com/img.jpg", "Image description"),
            ),
            sourceNotice = "Source: Example.com",
        )

        assertEquals("test-article", article.id)
        assertEquals("Test Article", article.title)
        assertEquals(4, article.blocks.size)
        assertTrue(article.blocks.any { it is ArticleBlock.Heading })
        assertTrue(article.blocks.any { it is ArticleBlock.Paragraph })
        assertTrue(article.blocks.any { it is ArticleBlock.CodeBlock })
        assertTrue(article.blocks.any { it is ArticleBlock.Image })
    }

    @Test
    fun feedSettings_hasDefaultValues() {
        val settings = FeedSettings.defaults()
        assertEquals(1.0f, settings.fontScale)
        assertEquals(1.25f, settings.lineHeightScale)
    }

    @Test
    fun shareText_usesTitleAndNormalizedUrl() {
        val article = article(url = "https://habr.com/ru/articles/1/?a=1&amp;b=2")

        assertEquals(
            "Test Article\nhttps://habr.com/ru/articles/1/?a=1&b=2",
            article.shareText(),
        )
    }

    @Test
    fun markdownText_exportsArticleBody() {
        val markdown = article().markdownText()

        assertTrue(markdown.contains("# Test Article"))
        assertTrue(markdown.contains("Original: https://example.com/article"))
        assertTrue(markdown.contains("This is a paragraph."))
    }

    @Test
    fun normalizedExternalUrl_rejectsNonHttpUrls() {
        assertEquals("https://example.com/?a=1&b=2", " https://example.com/?a=1&amp;b=2 ".normalizedExternalUrl())
        assertEquals(null, "javascript:alert(1)".normalizedExternalUrl())
        assertEquals(null, "/relative/path".normalizedExternalUrl())
    }

    @Test
    fun habrArticleIdFromUrl_extractsModernAndLegacyArticleLinks() {
        assertEquals("habr-123", "https://habr.com/ru/articles/123/?utm=feed".habrArticleIdFromUrl())
        assertEquals("habr-456", "https://www.habr.com/en/post/456/#comments".habrArticleIdFromUrl())
        assertEquals("habr-789", "https://m.habr.com/ru/articles/789/".habrArticleIdFromUrl())
        assertEquals("habr-321", "https://habr.com/ru/companies/acme/articles/321/".habrArticleIdFromUrl())
        assertEquals("habr-654", "https://habr.com/ru/news/654/comments/".habrArticleIdFromUrl())
        assertEquals(null, "https://habr.com/ru/hubs/kotlin/".habrArticleIdFromUrl())
        assertEquals(null, "https://example.com/articles/123/".habrArticleIdFromUrl())
    }

    private fun article(url: String = "https://example.com/article"): ArticleContent = ArticleContent(
        id = "test-article",
        title = "Test Article",
        url = url,
        imageUrl = "https://example.com/image.jpg",
        author = Author("author-1", "Test Author", "https://example.com/author"),
        publishedAt = "2026-05-02",
        tags = listOf(Tag("tag1", "Tag 1")),
        hubs = listOf(Hub("hub1", "Hub 1")),
        blocks = listOf(
            ArticleBlock.Heading(1, listOf(InlineNode.Text("Introduction"))),
            ArticleBlock.Paragraph(listOf(InlineNode.Text("This is a paragraph."))),
            ArticleBlock.CodeBlock("kotlin", "fun main() { println(\"Hello\") }"),
            ArticleBlock.Image("https://example.com/img.jpg", "Image description"),
        ),
        sourceNotice = "Source: Example.com",
    )
}
