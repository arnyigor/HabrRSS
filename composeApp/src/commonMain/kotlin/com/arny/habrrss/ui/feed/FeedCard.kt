package com.arny.habrrss.ui.feed

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arny.habrrss.domain.models.Author
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
                )

                // Magazine mode: large image below title
                if (mode == FeedCardMode.Magazine && !item.imageUrl.isNullOrBlank()) {
                    Spacer(Modifier.height(12.dp))
                    FeedThumbnail(
                        imageUrl = item.imageUrl,
                        contentDescription = "Обложка статьи",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        requestSize = 1024,
                    )
                }

                Spacer(Modifier.height(10.dp))
                FeedMetaLine(item)

                // Hub/tag labels are already shown as chips below; strip the duplicated
                // "Хабы: ... Метки: ..." text that Habr embeds inside the summary.
                val summaryText = item.summary.withoutHabrMetadata()

                // Comfortable mode: small image on the right
                if (mode == FeedCardMode.Comfortable && !item.imageUrl.isNullOrBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = summaryText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        FeedThumbnail(
                            imageUrl = item.imageUrl,
                            contentDescription = "Обложка",
                            modifier = Modifier
                                .size(80.dp),
                            requestSize = 256,
                        )
                    }
                } else if (mode != FeedCardMode.CompactText && summaryText.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = summaryText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        humanReadableDate(item.publishedAt, item.publishedAtEpoch).takeIf { it.isNotBlank() }
            ?.let { date ->
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
    // Reading time is derived from summary+HTML on every call; cache per item so scrolling
    // through the list doesn't re-run regex work on each recomposition.
    val readingMinutes = remember(item.id) { item.estimatedReadingMinutes() }
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$readingMinutes мин чтения",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FeedCardActions(
    item: FeedItem,
    onBookmark: () -> Unit,
) {
    val scoreLabel = remember(item.id) { item.habrScoreLabel() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            text = scoreLabel,
            style = MaterialTheme.typography.labelLarge,
            color = if (scoreLabel.startsWith("+")) {
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
    // FlowRow wraps instead of truncating, so long tag names are fully visible.
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        hubs.take(3).forEach { CompactChip(it.title) }
        tags.take(6).forEach { CompactChip("#${it.title}") }
    }
}

internal fun String.withoutHabrMetadata(): String {
    // Only strip a *trailing* "Хабы:/Метки:/Теги:" footer. Cutting at the first occurrence
    // destroyed real description text whenever an article mentioned tags/hubs mid-body, which
    // left feed cards with no description at all. We therefore look at the LAST occurrence and
    // only treat it as a footer when nothing past it looks like prose.
    val labels = listOf("Хабы:", "Метки:", "Теги:")
    val cut = labels.firstNotNullOfOrNull { label ->
        val idx = lastIndexOf(label)
        if (idx >= 0 && isTrailingMetadataBlock(this, idx)) idx else null
    } ?: return this
    return substring(0, cut).trim()
}

private fun isTrailingMetadataBlock(text: String, labelIndex: Int): Boolean {
    val tail = text.substring(labelIndex)
    if (tail.length > 300) return false
    // A footer is just hub/tag tokens; real prose would contain sentence punctuation.
    return tail.none { it in ".!?" }
}

@Composable
private fun CompactChip(label: String) {
    Surface(
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

// — Previews —

private val sampleHubs = listOf(
    Hub(id = "1", title = "Kotlin"),
    Hub(id = "2", title = "Android"),
    Hub(id = "3", title = "Compose"),
)

private val sampleTags = listOf(
    Tag(id = "1", title = "RSS"),
    Tag(id = "2", title = "Jetpack"),
    Tag(id = "3", title = "UI"),
    Tag(id = "4", title = "Mobile"),
)

private val sampleAuthor = Author(
    id = "1",
    displayName = "Иван Петров",
    profileUrl = "https://habr.com/users/example/",
)

private fun sampleFeedItem(
    id: String = "1",
    title: String = "Как мы переписали приложение на Compose и не пожалели",
    summary: String = "Подробный рассказ о миграции большого Android-приложения на Jetpack Compose: преимущества, подводные камни и практические советы.",
    imageUrl: String? = "https://habrastorage.org/r/w1560/getpro/habr/upload_files/abc/def/image.jpg",
    isRead: Boolean = false,
    isBookmarked: Boolean = false,
) = FeedItem(
    id = id,
    feedId = "feed-1",
    title = title,
    summary = summary,
    url = "https://habr.com/article/example/",
    imageUrl = imageUrl,
    author = sampleAuthor,
    publishedAt = "Mon, 15 Jan 2024 12:00:00 +0300",
    publishedAtEpoch = 1705315200000L,
    tags = sampleTags,
    hubs = sampleHubs,
    rating = "+42",
    commentsCount = 15,
    isRead = isRead,
    isBookmarked = isBookmarked,
)

@androidx.compose.ui.tooling.preview.Preview(
    name = "Magazine mode",
    showBackground = true,
    widthDp = 400
)
@Composable
private fun FeedCardPreview_Magazine() {
    MaterialTheme {
        FeedCard(
            item = sampleFeedItem(),
            selected = false,
            mode = FeedCardMode.Magazine,
            onClick = {},
            onBookmark = {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(
    name = "Comfortable mode",
    showBackground = true,
    widthDp = 400
)
@Composable
private fun FeedCardPreview_Comfortable() {
    MaterialTheme {
        FeedCard(
            item = sampleFeedItem(),
            selected = false,
            mode = FeedCardMode.Comfortable,
            onClick = {},
            onBookmark = {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(
    name = "Compact text mode",
    showBackground = true,
    widthDp = 400
)
@Composable
private fun FeedCardPreview_CompactText() {
    MaterialTheme {
        FeedCard(
            item = sampleFeedItem(imageUrl = null, summary = ""),
            selected = false,
            mode = FeedCardMode.CompactText,
            onClick = {},
            onBookmark = {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(
    name = "Read & bookmarked",
    showBackground = true,
    widthDp = 400
)
@Composable
private fun FeedCardPreview_ReadBookmarked() {
    MaterialTheme {
        FeedCard(
            item = sampleFeedItem(
                isRead = true,
                isBookmarked = true,
                title = "Прочитанная статья с закладкой",
            ),
            selected = false,
            mode = FeedCardMode.Comfortable,
            onClick = {},
            onBookmark = {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(
    name = "Selected state",
    showBackground = true,
    widthDp = 400
)
@Composable
private fun FeedCardPreview_Selected() {
    MaterialTheme {
        FeedCard(
            item = sampleFeedItem(),
            selected = true,
            mode = FeedCardMode.Magazine,
            onClick = {},
            onBookmark = {},
        )
    }
}
