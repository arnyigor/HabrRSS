package com.arny.habrrss.ui.article

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.arny.habrrss.domain.models.ArticleContent
import com.arny.habrrss.domain.models.CommentNode
import com.arny.habrrss.domain.models.FeedItem
import com.arny.habrrss.domain.models.FeedSettings
import com.arny.habrrss.ui.components.humanReadableDate

@Composable
internal fun ArticleFooterSections(
    article: ArticleContent,
    comments: List<CommentNode>,
    relatedArticles: List<FeedItem>,
    isLoadingExtras: Boolean,
    onRelatedArticleSelected: (String) -> Unit,
    onHabrArticleUrlSelected: (String, String) -> Unit,
    modifier: Modifier,
) {
    val actions = rememberArticleActions()
    val validUrl = article.url.normalizedExternalUrl()
    fun openArticleLink(url: String) {
        val articleId = url.habrArticleIdFromUrl()
        if (articleId != null) {
            onHabrArticleUrlSelected(articleId, url)
        } else {
            actions.openUrl(url)
        }
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when {
            comments.isNotEmpty() -> {
                HorizontalDivider()
                CommentsSection(
                    comments = comments,
                    openOriginal = { validUrl?.let(actions::openUrl) },
                    showOpenButton = validUrl != null,
                    onLinkClick = ::openArticleLink,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            isLoadingExtras -> {
                HorizontalDivider()
                ExtrasLoading(modifier = Modifier.fillMaxWidth())
            }

            else -> {
                HorizontalDivider()
                OpenOriginalButton(
                    openOriginal = { validUrl?.let(actions::openUrl) },
                    showOpenButton = validUrl != null,
                )
            }
        }

        if (relatedArticles.isNotEmpty()) {
            HorizontalDivider()
            RelatedArticlesSection(
                articles = relatedArticles,
                onArticleSelected = onRelatedArticleSelected,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CommentsSection(
    comments: List<CommentNode>,
    openOriginal: () -> Unit,
    showOpenButton: Boolean,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = FeedSettings.defaults()
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Комментарии (${comments.sumOf { it.totalCount() }})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            OpenOriginalButton(
                openOriginal = openOriginal,
                showOpenButton = showOpenButton
            )
        }
        comments.forEach { comment ->
            CommentItem(
                comment = comment,
                settings = settings,
                depth = 0,
                onLinkClick = onLinkClick,
            )
        }
    }
}

/** Max visual indent levels for nested replies; deeper threads align at this level. */
private const val MAX_COMMENT_DEPTH = 5
/** Fixed horizontal indent per nesting level (dp). */
private const val COMMENT_INDENT = 16

@Composable
private fun CommentItem(
    comment: CommentNode,
    settings: FeedSettings,
    depth: Int,
    onLinkClick: (String) -> Unit,
) {
    // Indent nested replies by a fixed step, but stop indenting past MAX_COMMENT_DEPTH so that
    // deeply threaded replies don't compound the parent's padding and shrink to an unreadable width.
    val indent = if (depth == 0 || depth > MAX_COMMENT_DEPTH) 0.dp else COMMENT_INDENT.dp
    Column(modifier = Modifier.fillMaxWidth().padding(start = indent)) {
        // Parent comment card. Children are rendered as SIBLINGS of this Surface (below), never
        // inside it — otherwise nested replies would be drawn on top of the parent's background/border.
        Surface(
            modifier = Modifier.fillMaxWidth(),
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
        if (comment.children.isNotEmpty()) {
            // Children live OUTSIDE the parent Surface so the parent's border/background never
            // overlaps them, and the parent's 12.dp content padding doesn't compound into width.
            Column(
                modifier = Modifier.padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                comment.children.forEach { child ->
                    CommentItem(
                        comment = child,
                        settings = settings,
                        depth = depth + 1,
                        onLinkClick = onLinkClick,
                    )
                }
            }
        }
    }
}

private fun CommentNode.totalCount(): Int = 1 + children.sumOf { it.totalCount() }

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
private fun RelatedArticlesSection(
    articles: List<FeedItem>,
    onArticleSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
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
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
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
            ) {
                if (item.imageUrl != null) {
                    AsyncImage(
                        model = item.imageUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
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