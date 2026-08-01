package com.arny.habrrss.ui.article

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.arny.habrrss.domain.models.ArticleBlock

/**
 * A single search hit inside one article block. [ranges] are offsets into [text]
 * (which is the plain text of the block).
 */
internal data class ArticleSearchMatch(
    val blockIndex: Int,
    val text: String,
    val ranges: List<IntRange>,
)

/**
 * Returns all blocks that contain [query] (case-insensitive) together with the
 * exact text ranges of every occurrence.
 */
internal fun findSearchMatches(blocks: List<ArticleBlock>, query: String): List<ArticleSearchMatch> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    return blocks.mapIndexedNotNull { index, block ->
        val text = block.blockText()
        val ranges = highlightRanges(text, q)
        if (ranges.isEmpty()) null else ArticleSearchMatch(blockIndex = index, text = text, ranges = ranges)
    }
}

/** Case-insensitive substring search. Returns empty list when nothing matches. */
internal fun highlightRanges(text: String, query: String): List<IntRange> {
    val q = query.trim()
    if (q.isEmpty() || text.isEmpty()) return emptyList()
    val lowerText = text.lowercase()
    val lowerQuery = q.lowercase()
    val result = mutableListOf<IntRange>()
    var start = 0
    while (true) {
        val index = lowerText.indexOf(lowerQuery, start)
        if (index < 0) break
        result.add(index until (index + q.length))
        start = index + q.length
    }
    return result
}

private const val HIGHLIGHT_BACKGROUND = 0xFFFFE082
private const val CURRENT_MATCH_BACKGROUND = 0xFFFFB300

/**
 * Returns a copy of [this] with all [query] occurrences highlighted.
 * [currentRange] (optional) is drawn with a stronger color so the user can
 * see the active match while others stay visible.
 */
internal fun AnnotatedString.withSearchHighlight(
    query: String,
    currentRange: IntRange? = null,
): AnnotatedString {
    val ranges = highlightRanges(text, query)
    if (ranges.isEmpty()) return this
    return buildAnnotatedString {
        append(this@withSearchHighlight)
        ranges.forEach { range ->
            val isCurrent = currentRange != null &&
                range.first == currentRange.first && range.last == currentRange.last
            addStyle(
                SpanStyle(background = Color(if (isCurrent) CURRENT_MATCH_BACKGROUND else HIGHLIGHT_BACKGROUND)),
                range.first,
                range.last + 1,
            )
        }
    }
}
