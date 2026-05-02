package com.arny.habrrss

import com.arny.habrrss.domain.models.ArticleBlock
import com.arny.habrrss.domain.models.ArticleContent
import com.arny.habrrss.domain.models.FeedItem
import com.arny.habrrss.domain.models.FeedKind
import com.arny.habrrss.domain.models.InlineNode
import com.arny.habrrss.presentation.ReaderUiState
import kotlin.math.ceil

internal enum class HabrPublicationSection(
    val label: String,
    val kind: FeedKind?,
) {
    Articles("СТАТЬИ", FeedKind.All),
    Posts("ПОСТЫ", FeedKind.Posts),
    News("НОВОСТИ", FeedKind.News),
    Hubs("ХАБЫ", null),
    Authors("АВТОРЫ", null),
    Companies("КОМПАНИИ", null),
}

internal data class HabrFeedTabState(
    val section: HabrPublicationSection,
    val feedId: String?,
    val count: Int?,
    val selected: Boolean,
    val enabled: Boolean,
)

internal fun ReaderUiState.habrFeedTabs(): List<HabrFeedTabState> {
    val selectedKind = feeds.firstOrNull { it.id == activeFeedId }?.kind
    return HabrPublicationSection.entries.map { section ->
        val feed = section.kind?.let { kind -> feeds.firstOrNull { it.kind == kind } }
        val selected = section.kind != null && section.kind == selectedKind
        HabrFeedTabState(
            section = section,
            feedId = feed?.id,
            count = if (selected) visibleItems.size else null,
            selected = selected,
            enabled = feed != null,
        )
    }
}

internal fun FeedItem.estimatedReadingMinutes(): Int {
    return estimateReadingMinutes(summary, descriptionHtml.orEmpty())
}

internal fun ArticleContent.estimatedReadingMinutes(): Int {
    return estimateReadingMinutes(blocks.joinToString(" ") { it.plainTextForReadingTime() })
}

internal fun FeedItem.habrScoreLabel(): String {
    return rating?.trim()?.takeIf { it.isNotBlank() } ?: "0"
}

internal fun FeedItem.habrCommentsLabel(): String {
    return commentsCount?.coerceAtLeast(0)?.toString() ?: "0"
}

private fun estimateReadingMinutes(vararg parts: String): Int {
    val text = parts.joinToString(" ")
        .replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
    val words = text.split(Regex("\\s+")).count { it.isNotBlank() }
    return ceil(words.coerceAtLeast(1) / 180.0).toInt().coerceAtLeast(1)
}

private fun ArticleBlock.plainTextForReadingTime(): String = when (this) {
    is ArticleBlock.CodeBlock -> code
    is ArticleBlock.Heading -> inline.plainTextForReadingTime()
    is ArticleBlock.Image -> alt.orEmpty()
    is ArticleBlock.ListBlock -> items.flatten().joinToString(" ") { it.plainTextForReadingTime() }
    is ArticleBlock.Paragraph -> inline.plainTextForReadingTime()
    is ArticleBlock.Quote -> blocks.joinToString(" ") { it.plainTextForReadingTime() }
    is ArticleBlock.TableBlock -> rows.flatten().flatten().joinToString(" ") { it.plainTextForReadingTime() }
    is ArticleBlock.UnknownHtml -> html
}

private fun List<InlineNode>.plainTextForReadingTime(): String = joinToString("") { node ->
    when (node) {
        is InlineNode.Bold -> node.children.plainTextForReadingTime()
        is InlineNode.Code -> node.value
        is InlineNode.Italic -> node.children.plainTextForReadingTime()
        is InlineNode.Link -> node.text
        is InlineNode.Text -> node.value
    }
}
