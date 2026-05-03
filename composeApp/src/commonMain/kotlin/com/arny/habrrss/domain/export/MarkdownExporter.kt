package com.arny.habrrss.domain.export

import com.arny.habrrss.domain.models.ArticleBlock
import com.arny.habrrss.domain.models.ArticleContent
import com.arny.habrrss.domain.models.InlineNode

class MarkdownExporter {
    fun export(article: ArticleContent): String = buildString {
        appendLine("# ${article.title}")
        article.author?.let { appendLine() ; appendLine("Author: ${it.displayName}") }
        article.publishedAt?.let { appendLine("Published: $it") }
        appendLine("Original: ${article.url}")
        appendLine()
        article.blocks.forEach { block ->
            appendBlock(block)
            appendLine()
        }
    }.trimEnd()

    private fun StringBuilder.appendBlock(block: ArticleBlock) {
        when (block) {
            is ArticleBlock.CodeBlock -> {
                appendLine("```${block.language.orEmpty()}")
                appendLine(block.code)
                appendLine("```")
            }
            is ArticleBlock.Heading -> appendLine("${"#".repeat(block.level.coerceIn(1, 6))} ${block.inline.markdown()}")
            is ArticleBlock.Image -> appendLine("![${block.alt.orEmpty()}](${block.url})")
            is ArticleBlock.ListBlock -> block.items.forEachIndexed { index, blocks ->
                val marker = if (block.ordered) "${index + 1}." else "-"
                append(marker)
                append(" ")
                appendLine(blocks.joinToString(" ") { it.plainText() })
            }
            is ArticleBlock.Paragraph -> appendLine(block.inline.markdown())
            is ArticleBlock.Quote -> block.blocks.forEach { appendLine("> ${it.plainText()}") }
            is ArticleBlock.Spoiler -> {
                appendLine("<details>")
                appendLine("<summary>${block.title}</summary>")
                appendLine()
                block.blocks.forEach { appendBlock(it) }
                appendLine("</details>")
            }
            is ArticleBlock.TableBlock -> appendLine(block.plainText())
            is ArticleBlock.UnknownHtml -> appendLine(block.html)
        }
    }

    private fun List<InlineNode>.markdown(): String = joinToString("") { node ->
        when (node) {
            is InlineNode.Bold -> "**${node.children.markdown()}**"
            is InlineNode.Code -> "`${node.value}`"
            is InlineNode.Italic -> "_${node.children.markdown()}_"
            is InlineNode.Link -> "[${node.text}](${node.url})"
            is InlineNode.Text -> node.value
        }
    }

    private fun ArticleBlock.plainText(): String = when (this) {
        is ArticleBlock.CodeBlock -> code
        is ArticleBlock.Heading -> inline.markdown()
        is ArticleBlock.Image -> alt.orEmpty()
        is ArticleBlock.ListBlock -> items.flatten().joinToString(" ") { it.plainText() }
        is ArticleBlock.Paragraph -> inline.markdown()
        is ArticleBlock.Quote -> blocks.joinToString(" ") { it.plainText() }
        is ArticleBlock.Spoiler -> blocks.joinToString(" ") { it.plainText() }
        is ArticleBlock.TableBlock -> rows.flatten().flatten().joinToString(" ") { it.plainText() }
        is ArticleBlock.UnknownHtml -> html
    }
}
