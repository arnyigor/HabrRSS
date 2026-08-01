package com.arny.habrrss.ui.article

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import com.arny.habrrss.domain.export.MarkdownExporter
import com.arny.habrrss.domain.models.ArticleBlock
import com.arny.habrrss.domain.models.ArticleContent
import com.arny.habrrss.domain.models.InlineNode

internal const val LINK_TAG = "url"

internal fun List<InlineNode>.toAnnotatedString(): AnnotatedString = buildAnnotatedString {
    forEach { node ->
        appendInlineNode(node)
    }
}

private fun AnnotatedString.Builder.appendInlineNode(node: InlineNode) {
    when (node) {
        is InlineNode.Text -> append(node.value)
        is InlineNode.Code -> withStyle(
            SpanStyle(
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF7A3E00),
                background = Color(0xFFFFF3D6),
            )
        ) {
            append(node.value)
        }
        is InlineNode.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            node.children.forEach { appendInlineNode(it) }
        }
        is InlineNode.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
            node.children.forEach { appendInlineNode(it) }
        }
        is InlineNode.Link -> {
            pushStringAnnotation(tag = LINK_TAG, annotation = node.url.normalizedExternalUrl() ?: node.url)
            withStyle(
                SpanStyle(
                    color = Color(0xFF1C73A8),
                    textDecoration = TextDecoration.Underline,
                )
            ) {
                append(node.text)
            }
            pop()
        }
    }
}

internal fun String.toHighlightedCode(language: String?): AnnotatedString {
    val keywords = when (language?.lowercase()) {
        "kotlin", "kt" -> setOf(
            "actual", "as", "break", "class", "continue", "data", "else", "expect", "false",
            "for", "fun", "if", "in", "interface", "is", "null", "object", "override", "package",
            "private", "return", "sealed", "suspend", "true", "val", "var", "when", "while",
        )
        "java" -> setOf(
            "abstract", "boolean", "break", "case", "class", "else", "false", "final", "for",
            "if", "import", "interface", "new", "null", "private", "public", "return", "static",
            "true", "void", "while",
        )
        else -> emptySet()
    }
    if (keywords.isEmpty()) return AnnotatedString(this)

    val code = this
    val keywordColor = Color(0xFF005FAD)
    val stringColor = Color(0xFF8A4B00)
    val commentColor = Color(0xFF6A737D)

    return buildAnnotatedString {
        var index = 0
        val tokenRegex = Regex("""//.*|"(?:\\.|[^"\\])*"|\b[A-Za-z_][A-Za-z0-9_]*\b""")
        tokenRegex.findAll(code).forEach { match ->
            if (match.range.first > index) append(code.substring(index, match.range.first))
            val token = match.value
            val style = when {
                token.startsWith("//") -> SpanStyle(color = commentColor, fontStyle = FontStyle.Italic)
                token.startsWith("\"") -> SpanStyle(color = stringColor)
                token in keywords -> SpanStyle(color = keywordColor, fontWeight = FontWeight.Bold)
                else -> null
            }
            if (style != null) {
                withStyle(style) { append(token) }
            } else {
                append(token)
            }
            index = match.range.last + 1
        }
        if (index < code.length) append(code.substring(index))
    }
}

internal fun List<InlineNode>.plainText(): String = joinToString("") { node ->
    when (node) {
        is InlineNode.Bold -> node.children.plainText()
        is InlineNode.Code -> node.value
        is InlineNode.Italic -> node.children.plainText()
        is InlineNode.Link -> node.text
        is InlineNode.Text -> node.value
    }
}

internal fun ArticleBlock.blockText(): String = when (this) {
    is ArticleBlock.CodeBlock -> code
    is ArticleBlock.Heading -> inline.plainText()
    is ArticleBlock.Image -> alt ?: url
    is ArticleBlock.ListBlock -> items.flatten().joinToString(" ") { it.blockText() }
    is ArticleBlock.Paragraph -> inline.plainText()
    is ArticleBlock.Quote -> blocks.joinToString(" ") { it.blockText() }
    is ArticleBlock.Spoiler -> blocks.joinToString(" ") { it.blockText() }
    is ArticleBlock.TableBlock -> rows.flatten().flatten().joinToString(" ") { it.blockText() }
    is ArticleBlock.UnknownHtml -> html
}

internal fun ArticleContent.shareText(): String = buildString {
    append(title)
    url.normalizedExternalUrl()?.let {
        appendLine()
        append(it)
    }
}

internal fun ArticleContent.markdownText(): String = MarkdownExporter().export(this)

internal fun String.normalizedExternalUrl(): String? {
    val normalized = trim().replace("&amp;", "&")
    return normalized.takeIf { it.startsWith("http://") || it.startsWith("https://") }
}
