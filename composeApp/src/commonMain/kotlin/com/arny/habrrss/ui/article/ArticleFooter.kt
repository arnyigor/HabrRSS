package com.arny.habrrss.ui.article

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
        if (comments.isNotEmpty()) {
            HorizontalDivider()
            CommentsSection(
                comments = comments,
                openOriginal = { validUrl?.let(actions::openUrl) },
                showOpenButton = validUrl != null,
                onLinkClick = ::openArticleLink,
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (isLoadingExtras) {
            HorizontalDivider()
            ExtrasLoading(modifier = Modifier.fillMaxWidth())
        } else {
            HorizontalDivider()
            CommentsUnavailable(
                openOriginal = { validUrl?.let(actions::openUrl) },
                showOpenButton = validUrl != null,
                modifier = Modifier.fillMaxWidth(),
            )
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
            if (showOpenButton) {
                OutlinedButton(onClick = openOriginal) {
                    Text("Перейти к оригиналу")
                }
            }
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

@Composable
private fun CommentItem(
    comment: CommentNode,
    settings: FeedSettings,
    depth: Int,
    onLinkClick: (String) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (depth == 0) 0.dp else 12.dp),
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
                Box(
                    modifier = Modifier
                        .width(3.dp)
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
                        modifier = Modifier.widthIn(max = 860.dp),
                        onLinkClick = onLinkClick,
                    )
                }
                if (comment.children.isNotEmpty()) {
                    Column(
                        modifier = Modifier.padding(top = 4.dp),
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
    }
}

private fun CommentNode.totalCount(): Int = 1 + children.sumOf { it.totalCount() }

@Composable
private fun CommentsUnavailable(
    openOriginal: () -> Unit,
    showOpenButton: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(0.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Комментарии", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (showOpenButton) {
                OutlinedButton(onClick = openOriginal) {
                    Text("Перейти к оригиналу")
                    Spacer(Modifier.width(4.dp))
                }
            }
        }
    }
}

@Composable
private fun ExtrasLoading(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.width(20.dp))
        Text(
            text = "Загружаем комментарии…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RelatedArticlesSection(
    articles: List<FeedItem>,
    onArticleSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Похожие статьи",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        articles.forEach { item ->
            RelatedArticleCard(
                item = item,
                onClick = { onArticleSelected(item.id) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun RelatedArticleCard(
    item: FeedItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                item.author?.displayName?.let { author ->
                    Text(
                        text = author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                humanReadableDate(item.publishedAt, item.publishedAtEpoch).takeIf { it.isNotBlank() }?.let { date ->
                    Text(
                        text = "  $date",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
