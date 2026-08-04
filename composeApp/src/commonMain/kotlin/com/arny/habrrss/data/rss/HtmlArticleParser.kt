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
                // Use wholeText() to preserve whitespace in code blocks
                val codeText = codeElement?.wholeText() ?: element.wholeText()
                listOf(ArticleBlock.CodeBlock(language, codeText))
            }
            "blockquote" -> {
                val innerBlocks = parse(element.html(), baseUrl)
                listOf(ArticleBlock.Quote(innerBlocks))
            }
            "details" -> {
                val summaryElement = element.selectFirst("summary")
                val title = summaryElement?.text()?.trim()?.takeIf { it.isNotBlank() } ?: "Спойлер"
                summaryElement?.remove()

                val innerBlocks = parse(element.html(), baseUrl)
                listOf(ArticleBlock.Spoiler(title, innerBlocks))
            }
            "ul", "ol" -> {
                val ordered = element.tagName().lowercase() == "ol"
                val items = element.children()
                    .filter { it.tagName().lowercase() == "li" }
                    .map { li -> parseListItem(li, baseUrl) }
                listOf(ArticleBlock.ListBlock(ordered, items))
            }
            "table" -> listOf(parseTable(element, baseUrl))
            "img" -> {
                listOf(ArticleBlock.Image(
                    url = normalizeUrl(element.imageUrlCandidate(), baseUrl),
                    alt = element.attr("alt").takeIf { it.isNotBlank() }
                ))
            }
            "figure" -> {
                // Handle figure with figcaption and nested content
                val images = element.select("img").map { img ->
                    ArticleBlock.Image(
                        url = normalizeUrl(img.imageUrlCandidate(), baseUrl),
                        alt = img.attr("alt").takeIf { it.isNotBlank() }
                    )
                }
                val figcaption = element.selectFirst("figcaption")?.let { fig ->
                    ArticleBlock.Paragraph(parseInline(fig, baseUrl))
                }
                val otherContent = element.children()
                    .filter { it.tagName().lowercase() !in listOf("img", "figcaption") }
                    .flatMap { parseTopLevelElement(it, baseUrl) }

                val allContent = mutableListOf<ArticleBlock>()
                allContent.addAll(images)
                figcaption?.let { allContent.add(it) }
                allContent.addAll(otherContent)

                allContent.ifEmpty { textParagraph(element) }
            }
            "picture" -> {
                // Handle picture with source elements
                val img = element.selectFirst("img")
                val sources = element.select("source")
                // Prefer source with srcset, fallback to img
                val imageUrl: String? = sources.firstOrNull { it.attr("srcset").isNotBlank() }
                    ?.attr("srcset")
                    ?.firstSrcSetUrl()
                    ?: img?.imageUrlCandidate()?.takeIf { it.isNotBlank() }

                if (imageUrl != null) {
                    listOf(ArticleBlock.Image(
                        url = normalizeUrl(imageUrl, baseUrl),
                        alt = img?.attr("alt")?.takeIf { it.isNotBlank() }
                    ))
                } else {
                    val inner = element.children().flatMap { parseTopLevelElement(it, baseUrl) }
                    inner.ifEmpty { textParagraph(element) }
                }
            }
            "div", "section" -> {
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

    private fun parseTable(element: Element, baseUrl: String?): ArticleBlock.TableBlock {
        val rows = mutableListOf<List<List<ArticleBlock>>>()

        // Process thead, tbody, tfoot
        val sections = element.select("thead, tbody, tfoot")
        if (sections.isEmpty()) {
            // No sections, treat direct tr children as rows
            val trs = element.select("tr")
            for (tr in trs) {
                val row = parseTableRow(tr, baseUrl)
                if (row.isNotEmpty()) rows.add(row)
            }
        } else {
            for (section in sections) {
                val trs = section.select("tr")
                for (tr in trs) {
                    val row = parseTableRow(tr, baseUrl)
                    if (row.isNotEmpty()) rows.add(row)
                }
            }
        }

        return ArticleBlock.TableBlock(rows)
    }

    private fun parseTableRow(tr: Element, baseUrl: String?): List<List<ArticleBlock>> {
        return tr.children()
            .filter { it.tagName().lowercase() in listOf("td", "th") }
            .map { cell ->
                val cellBlocks = parse(cell.html(), baseUrl)
                if (cellBlocks.isEmpty()) {
                    listOf(ArticleBlock.Paragraph(listOf(InlineNode.Text(cell.text().trim()))))
                } else {
                    cellBlocks
                }
            }
    }

    private fun parseListItem(li: Element, baseUrl: String?): List<ArticleBlock> {
        val blocks = mutableListOf<ArticleBlock>()
        val inline = mutableListOf<InlineNode>()

        fun flushInline() {
            if (inline.isNotEmpty()) {
                blocks.add(ArticleBlock.Paragraph(inline.toList()))
                inline.clear()
            }
        }

        for (node in li.childNodes()) {
            when (node) {
                is TextNode -> {
                    val text = node.text()
                    if (text.isNotBlank()) inline.add(InlineNode.Text(text))
                }
                is Element -> {
                    when (node.tagName().lowercase()) {
                        "p" -> {
                            flushInline()
                            blocks.add(ArticleBlock.Paragraph(parseInline(node, baseUrl)))
                        }
                        "ul", "ol", "pre", "blockquote", "details", "table", "figure", "picture", "img", "div", "section" -> {
                            flushInline()
                            blocks.addAll(parseTopLevelElement(node, baseUrl))
                        }
                        "br" -> inline.add(InlineNode.Text("\n"))
                        else -> inline.addAll(parseInlineElement(node, baseUrl))
                    }
                }
            }
        }
        flushInline()

        return blocks.ifEmpty { textParagraph(li) }
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
                    nodes.addAll(parseInlineElement(node, baseUrl))
                }
            }
        }
        return nodes
    }

    private fun parseInlineElement(node: Element, baseUrl: String?): List<InlineNode> {
        return when (node.tagName().lowercase()) {
            "a" -> listOf(InlineNode.Link(node.text(), normalizeUrl(node.attr("href"), baseUrl)))
            "code" -> listOf(InlineNode.Code(node.text()))
            "strong", "b" -> listOf(InlineNode.Bold(parseInline(node, baseUrl)))
            "em", "i" -> listOf(InlineNode.Italic(parseInline(node, baseUrl)))
            "sup", "sub" -> {
                // Wrap in italic as visual indicator (could be enhanced later)
                listOf(InlineNode.Italic(parseInline(node, baseUrl)))
            }
            "kbd" -> listOf(InlineNode.Code(node.text()))
            "mark" -> listOf(InlineNode.Bold(parseInline(node, baseUrl)))
            "del" -> parseInline(node, baseUrl)
            "br" -> listOf(InlineNode.Text("\n"))
            else -> parseInline(node, baseUrl)
        }
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

    private fun Element.imageUrlCandidate(): String =
        attr("src")
            .ifBlank { attr("data-src") }
            .ifBlank { attr("data-original") }
            .ifBlank { attr("srcset").firstSrcSetUrl() }

    private fun String.firstSrcSetUrl(): String =
        split(",")
            .firstOrNull()
            ?.trim()
            ?.split(Regex("\\s+"))
            ?.firstOrNull()
            .orEmpty()
}
