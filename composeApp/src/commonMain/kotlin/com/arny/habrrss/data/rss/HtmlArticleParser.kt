package com.arny.habrrss.data.rss

import com.arny.habrrss.domain.models.ArticleBlock
import com.arny.habrrss.domain.models.InlineNode
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.TextNode

object HtmlArticleParser {

    fun parse(html: String, baseUrl: String? = null): List<ArticleBlock> {
        if (html.isBlank()) return emptyList()

        val doc = Ksoup.parseBodyFragment(html)
        val body = doc.body()
        val blocks = mutableListOf<ArticleBlock>()

        if (body.children().isEmpty()) {
            val text = body.text().trim()
            if (text.isNotEmpty()) {
                return listOf(ArticleBlock.Paragraph(listOf(InlineNode.Text(text))))
            }
            return emptyList()
        }

        for (child in body.children()) {
            blocks.addAll(parseTopLevelElement(child, baseUrl))
        }

        return blocks.ifEmpty {
            listOf(ArticleBlock.Paragraph(listOf(InlineNode.Text(body.text().trim()))))
        }
    }

    private fun parseTopLevelElement(element: Element, baseUrl: String?): List<ArticleBlock> {
        return when (element.tagName().lowercase()) {
            "p" -> listOf(ArticleBlock.Paragraph(parseInline(element, baseUrl)))
            "h1", "h2", "h3", "h4", "h5", "h6" -> {
                val level = element.tagName().drop(1).toIntOrNull()?.coerceIn(1, 6) ?: 2
                listOf(ArticleBlock.Heading(level, parseInline(element, baseUrl)))
            }
            "pre" -> {
                val codeElement = element.selectFirst("code")
                val language = codeElement?.classNames()?.firstOrNull()?.removePrefix("language-")
                val codeText = codeElement?.text() ?: element.text()
                listOf(ArticleBlock.CodeBlock(language, codeText))
            }
            "blockquote" -> {
                val innerBlocks = parse(element.html(), baseUrl)
                listOf(ArticleBlock.Quote(innerBlocks))
            }
            "ul", "ol" -> {
                val ordered = element.tagName().lowercase() == "ol"
                val items = element.children()
                    .filter { it.tagName().lowercase() == "li" }
                    .map { li -> listOf(ArticleBlock.Paragraph(parseInline(li, baseUrl))) }
                listOf(ArticleBlock.ListBlock(ordered, items))
            }
            "img" -> {
                listOf(ArticleBlock.Image(
                    url = normalizeUrl(element.attr("src"), baseUrl),
                    alt = element.attr("alt").takeIf { it.isNotBlank() }
                ))
            }
            "div" -> {
                val inner = element.children().flatMap { parseTopLevelElement(it, baseUrl) }
                inner.ifEmpty { textParagraph(element) }
            }
            "figure", "picture", "section" -> {
                val inner = element.children().flatMap { parseTopLevelElement(it, baseUrl) }
                inner.ifEmpty { textParagraph(element) }
            }
            "br", "hr", "script", "style", "meta", "link", "source" -> emptyList()
            else -> {
                val text = element.text().trim()
                if (text.isNotEmpty()) listOf(ArticleBlock.Paragraph(parseInline(element, baseUrl))) else emptyList()
            }
        }
    }

    private fun textParagraph(element: Element): List<ArticleBlock> {
        val text = element.text().trim()
        return if (text.isNotEmpty()) listOf(ArticleBlock.Paragraph(listOf(InlineNode.Text(text)))) else emptyList()
    }

    private fun parseInline(element: Element, baseUrl: String?): List<InlineNode> {
        val nodes = mutableListOf<InlineNode>()
        for (node in element.childNodes()) {
            when {
                node is TextNode -> {
                    val text = node.text()
                    if (text.isNotBlank()) nodes.add(InlineNode.Text(text))
                }
                node is com.fleeksoft.ksoup.nodes.Element -> {
                    when (node.tagName().lowercase()) {
                        "a" -> nodes.add(InlineNode.Link(node.text(), normalizeUrl(node.attr("href"), baseUrl)))
                        "code" -> nodes.add(InlineNode.Code(node.text()))
                        "strong", "b" -> nodes.add(InlineNode.Bold(parseInline(node, baseUrl)))
                        "em", "i" -> nodes.add(InlineNode.Italic(parseInline(node, baseUrl)))
                        "br" -> Unit
                        else -> nodes.addAll(parseInline(node, baseUrl))
                    }
                }
            }
        }
        return nodes
    }

    private fun normalizeUrl(url: String, baseUrl: String?): String {
        val trimmed = url.trim()
        if (trimmed.isBlank() || baseUrl.isNullOrBlank()) return trimmed
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("mailto:")) {
            return trimmed
        }
        if (trimmed.startsWith("//")) return "https:$trimmed"
        if (trimmed.startsWith("#")) return baseUrl.substringBefore("#") + trimmed

        val origin = Regex("""^(https?://[^/]+)""").find(baseUrl)?.value ?: return trimmed
        return when {
            trimmed.startsWith("/") -> origin + trimmed
            else -> origin + "/" + trimmed
        }
    }
}
