package com.arny.habrrss.ui.feed

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arny.habrrss.domain.models.FeedItem
import com.arny.habrrss.domain.models.Hub
import com.arny.habrrss.domain.models.Tag
import com.arny.habrrss.presentation.FeedCardMode
import com.arny.habrrss.presentation.feed.estimatedReadingMinutes
import com.arny.habrrss.presentation.feed.habrCommentsLabel
import com.arny.habrrss.presentation.feed.habrScoreLabel
import com.arny.habrrss.ui.article.FeedThumbnail
import com.arny.habrrss.ui.components.humanReadableDate

@Composable
internal fun FeedCard(
    item: FeedItem,
    selected: Boolean,
    mode: FeedCardMode,
    onClick: () -> Unit,
    onBookmark: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(0.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(if (!item.isRead || selected) MaterialTheme.colorScheme.primary else Color.Transparent),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            ) {
                FeedCardAuthorLine(item)
                Spacer(Modifier.height(10.dp))
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = if (item.isRead) FontWeight.SemiBold else FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )

                // Magazine mode: large image below title
                if (mode == FeedCardMode.Magazine && !item.imageUrl.isNullOrBlank()) {
                    Spacer(Modifier.height(12.dp))
                    FeedThumbnail(
                        imageUrl = item.imageUrl,
                        contentDescription = "Обложка статьи",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    )
                }

                Spacer(Modifier.height(10.dp))
                FeedMetaLine(item)

                // Comfortable mode: small image on the right
                if (mode == FeedCardMode.Comfortable && !item.imageUrl.isNullOrBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = item.summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        FeedThumbnail(
                            imageUrl = item.imageUrl,
                            contentDescription = "Обложка",
                            modifier = Modifier
                                .size(80.dp)
                        )
                    }
                } else if (mode != FeedCardMode.CompactText && item.summary.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = item.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (mode == FeedCardMode.Magazine) 4 else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.height(10.dp))
                MetadataRow(item.hubs, item.tags)
                Spacer(Modifier.height(12.dp))
                FeedCardActions(item, onBookmark)
            }
        }
    }
}

@Composable
private fun FeedCardAuthorLine(item: FeedItem) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = item.author?.displayName?.firstOrNull()?.uppercaseChar()?.toString() ?: "H",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = item.author?.displayName ?: "Habr RSS",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        humanReadableDate(item.publishedAt, item.publishedAtEpoch).takeIf { it.isNotBlank() }?.let { date ->
            Text(
                text = "  $date",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun FeedMetaLine(item: FeedItem) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Простой", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text("${item.estimatedReadingMinutes()} мин", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("RSS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FeedCardActions(
    item: FeedItem,
    onBookmark: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            text = item.habrScoreLabel(),
            style = MaterialTheme.typography.labelLarge,
            color = if (item.habrScoreLabel().startsWith("+")) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = FontWeight.Bold,
        )
        IconButton(onClick = onBookmark, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = if (item.isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                contentDescription = if (item.isBookmarked) "Убрать из закладок" else "Сохранить",
                tint = if (item.isBookmarked) Color(0xFFFFA000) else MaterialTheme.colorScheme.outline,
            )
        }
        Text(
            text = item.habrCommentsLabel(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MetadataRow(
    hubs: List<Hub>,
    tags: List<Tag>,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        (hubs.take(2).map { it.title } + tags.take(3).map { "#${it.title}" })
            .take(4)
            .forEach { CompactChip(it) }
    }
}

@Composable
private fun CompactChip(label: String) {
    Surface(
        modifier = Modifier.widthIn(max = 180.dp),
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
