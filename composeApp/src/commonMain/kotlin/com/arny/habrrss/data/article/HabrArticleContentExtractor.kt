package com.arny.habrrss.data.article

import com.arny.habrrss.data.rss.HtmlArticleParser
import com.arny.habrrss.domain.models.ArticleBlock
import com.arny.habrrss.domain.models.ArticleContent
import com.arny.habrrss.domain.models.InlineNode
import com.arny.habrrss.domain.source.SourceUnavailableException
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element

class HabrArticleContentExtractor {
    fun extract(
        articleId: String,
        articleUrl: String,
        html: String,
    ): ArticleContent {
        val canonicalUrl = articleUrl.toCanonicalHabrUrl()
        val doc = Ksoup.parse(html)
        val body = doc.findArticleBody()
            ?: throw SourceUnavailableException("Article body not found: $canonicalUrl")

        body.removeNoise()

        val blocks = HtmlArticleParser.parse(body.html(), canonicalUrl)
        val textLength = blocks.sumOf { it.blockTextLength() }
        if (blocks.isEmpty() || textLength < MinFullArticleTextLength) {
            throw SourceUnavailableException("Article body is too short: $canonicalUrl")
        }

        return ArticleContent(
            id = articleId,
            title = doc.selectFirst("h1")?.text()?.trim()?.takeIf { it.isNotBlank() }
                ?: doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim()?.takeIf { it.isNotBlank() }
                ?: canonicalUrl,
            url = canonicalUrl,
            imageUrl = body.selectFirst("img")?.imageUrl(canonicalUrl)
                ?: doc.selectFirst("meta[property=og:image]")?.attr("content")?.trim()?.takeIf { it.isNotBlank() },
            author = null,
            publishedAt = null,
            tags = emptyList(),
            hubs = emptyList(),
            blocks = blocks,
            sourceNotice = "Полная статья загружена с Habr.",
        )
    }

    private fun Element.findArticleBody(): Element? {
        return selectFirst("#post-content-body .article-formatted-body")
            ?: selectFirst("[data-test-id=article-body] .article-formatted-body")
            ?: selectFirst(".tm-article-presenter__content .article-formatted-body")
            ?: selectFirst("#post-content-body")
            ?: selectFirst("[data-test-id=article-body]")
            ?: selectFirst(".article-formatted-body")
    }

    private fun Element.removeNoise() {
        select("script, style, noscript, svg, iframe").remove()
        select("a[href*=#habracut]").remove()
        select(".tm-article-presenter__footer, .tm-article-sticky-panel").remove()
    }

    private fun Element.imageUrl(baseUrl: String): String? {
        val value = attr("src")
            .ifBlank { attr("data-src") }
            .ifBlank { attr("data-original") }
            .trim()
        return value.takeIf { it.isNotBlank() }?.toAbsoluteUrl(baseUrl)
    }

    private fun String.toCanonicalHabrUrl(): String {
        val normalized = replace("&amp;", "&").trim()
        val withoutFragment = normalized.substringBefore("#")
        return withoutFragment.substringBefore("?")
    }

    private fun String.toAbsoluteUrl(baseUrl: String): String {
        return when {
            startsWith("http://") || startsWith("https://") -> this
            startsWith("//") -> "https:$this"
            startsWith("/") -> {
                val origin = Regex("""^(https?://[^/]+)""").find(baseUrl)?.value.orEmpty()
                origin + this
            }
            else -> this
        }
    }

    private fun ArticleBlock.blockTextLength(): Int {
        return when (this) {
            is ArticleBlock.CodeBlock -> code.length
            is ArticleBlock.Heading -> inline.inlineTextLength()
            is ArticleBlock.Image -> alt?.length ?: 0
            is ArticleBlock.ListBlock -> items.flatten().sumOf { it.blockTextLength() }
            is ArticleBlock.Paragraph -> inline.inlineTextLength()
            is ArticleBlock.Quote -> blocks.sumOf { it.blockTextLength() }
            is ArticleBlock.Spoiler -> blocks.sumOf { it.blockTextLength() }
            is ArticleBlock.TableBlock -> rows.flatten().flatten().sumOf { it.blockTextLength() }
            is ArticleBlock.UnknownHtml -> html.length
        }
    }

    private fun List<InlineNode>.inlineTextLength(): Int = sumOf { node ->
        when (node) {
            is InlineNode.Bold -> node.children.inlineTextLength()
            is InlineNode.Code -> node.value.length
            is InlineNode.Italic -> node.children.inlineTextLength()
            is InlineNode.Link -> node.text.length
            is InlineNode.Text -> node.value.length
        }
    }

    private companion object {
        const val MinFullArticleTextLength = 250
    }
}
