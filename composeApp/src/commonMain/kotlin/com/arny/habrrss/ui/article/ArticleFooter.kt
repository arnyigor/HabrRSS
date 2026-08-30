package com.arny.habrrss.ui.article

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.arny.habrrss.domain.models.CommentNode
import com.arny.habrrss.domain.models.FeedItem
import com.arny.habrrss.domain.models.FeedSettings
import com.arny.habrrss.ui.components.humanReadableDate

/** Flattened comment with its tree depth, for virtualized rendering inside a LazyColumn. */
internal data class FlatComment(val node: CommentNode, val depth: Int)

/**
 * Depth-first flatten of the comment tree. Rendering the whole tree as one composable composed
 * every nested Surface synchronously and froze the UI on threads with 2k+ comments; flattening
 * lets ArticleScreen emit each comment as its own lazy item (only visible ones are composed).
 */
internal fun flattenComments(roots: List<CommentNode>): List<FlatComment> {
    val out = mutableListOf<FlatComment>()
    fun dfs(nodes: List<CommentNode>, depth: Int) {
        for (node in nodes) {
            out += FlatComment(node, depth)
            dfs(node.children, depth + 1)
        }
    }
    dfs(roots, 0)
    return out
}

@Composable
internal fun CommentsHeader(
    comments: List<CommentNode>,
    openOriginal: () -> Unit,
    showOpenButton: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Комментарии (${comments.sumOf { it.totalCount() }})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        OpenOriginalButton(
            openOriginal = openOriginal,
            showOpenButton = showOpenButton,
        )
    }
}

/** Max visual indent levels for nested replies; deeper threads align at this level. */
private const val MAX_COMMENT_DEPTH = 5
/** Fixed horizontal indent per nesting level (dp). */
private const val COMMENT_INDENT = 16

@Composable
internal fun CommentItem(
    comment: CommentNode,
    settings: FeedSettings,
    depth: Int,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Indent nested replies by a fixed step, but stop indenting past MAX_COMMENT_DEPTH so that
    // deeply threaded replies don't compound the parent's padding and shrink to an unreadable width.
    val indent = if (depth == 0 || depth > MAX_COMMENT_DEPTH) 0.dp else COMMENT_INDENT.dp
    Surface(
        modifier = modifier.fillMaxWidth().padding(start = indent),
        color = if (depth == 0) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        },
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            if (depth > 0) {
                // Vertical accent line marking the reply level; fillMaxHeight keeps it spanning
                // the full card height regardless of how long the comment body is.
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                )
            }
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = comment.author?.displayName ?: "Аноним",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    comment.publishedAt?.let { date ->
                        humanReadableDate(date).takeIf { it.isNotBlank() }?.let { readable ->
                            Text(
                                text = " · $readable",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                comment.body.forEach { block ->
                    ArticleBlockView(
                        block = block,
                        settings = settings,
                        modifier = Modifier.fillMaxWidth(),
                        onLinkClick = onLinkClick,
                    )
                }
            }
        }
    }
}

private fun CommentNode.totalCount(): Int = 1 + children.sumOf { it.totalCount() }

/**
 * Bottom loading/empty state of the article footer (shown when there are no comments yet).
 * Related articles are rendered separately ABOVE the comments in ArticleScreen.
 */
@Composable
internal fun ArticleFooterTail(
    isLoadingExtras: Boolean,
    hasComments: Boolean,
    showOpenButton: Boolean,
    openOriginal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!hasComments) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HorizontalDivider()
            when {
                isLoadingExtras -> ExtrasLoading(modifier = Modifier.fillMaxWidth())
                else -> OpenOriginalButton(
                    openOriginal = openOriginal,
                    showOpenButton = showOpenButton,
                )
            }
        }
    }
}

@Composable
private fun OpenOriginalButton(
    openOriginal: () -> Unit,
    showOpenButton: Boolean,
) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showOpenButton) {
            OutlinedButton(onClick = openOriginal) {
                Text("Перейти к оригиналу")
                Spacer(Modifier.width(4.dp))
            }
        }
    }
}

@Composable
private fun ExtrasLoading(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.5.dp,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Загружаем комментарии…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun RelatedArticlesSection(
    articles: List<FeedItem>,
    onArticleSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    // Chevron buttons appear only when there is content to scroll to that side — on desktop they
    // are the primary way to scroll the carousel (mouse wheel scrolls vertically, not horizontally).
    val canScrollBackward by remember { derivedStateOf { listState.canScrollBackward } }
    val canScrollForward by remember { derivedStateOf { listState.canScrollForward } }
    val scrollPage: (Boolean) -> Unit = { forward ->
        coroutineScope.launch {
            listState.animateScrollBy(with(density) { (if (forward) 240 else -240).dp.toPx() })
        }
    }
    Column(
        modifier = modifier.padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Похожие статьи",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (canScrollBackward) {
                IconButton(onClick = { scrollPage(false) }) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Прокрутить похожие статьи назад",
                    )
                }
            }
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) {
                items(
                    articles,
                    key = { it.id }
                ) { item ->
                    RelatedArticleCard(
                        item = item,
                        onClick = { onArticleSelected(item.id) },
                    )
                }
            }
            if (canScrollForward) {
                IconButton(onClick = { scrollPage(true) }) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Прокрутить похожие статьи вперёд",
                    )
                }
            }
        }
    }
}

@Composable
private fun RelatedArticleCard(
    item: FeedItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.width(200.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column {
            // Preview image on top with a fixed height so the text block below always has room
            // and never overlaps the picture.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (!item.imageUrl.isNullOrBlank()) {
                    // SubcomposeAsyncImage (not plain AsyncImage) so we get explicit loading/error
                    // states: a spinner while the thumbnail fetches, and a broken-image placeholder
                    // if the URL is dead or the request fails — instead of a silent blank box.
                    SubcomposeAsyncImage(
                        model = item.imageUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        loading = { ImageLoadingPreview(Modifier.fillMaxSize()) },
                        error = { ImageErrorPreview(contentDescription = item.title, modifier = Modifier.fillMaxSize()) },
                    )
                } else {
                    Icon(
                        Icons.Filled.Image,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    )
                }
            }
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Title is shown in full (no ellipsis) — only the description below may be truncated.
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (item.summary.isNotBlank()) {
                    Text(
                        text = item.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val date = humanReadableDate(item.publishedAt, item.publishedAtEpoch)
                val metaText = listOfNotNull(item.author?.displayName, date.ifBlank { null })
                    .joinToString(" • ")
                if (metaText.isNotEmpty()) {
                    Text(
                        text = metaText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
