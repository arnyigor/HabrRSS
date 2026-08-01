package com.arny.habrrss.ui.feed

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arny.habrrss.domain.models.Hub
import com.arny.habrrss.domain.models.Tag
import com.arny.habrrss.presentation.ReaderUiState

@Composable
internal fun FeedFilterBar(
    state: ReaderUiState,
    onFeedSelected: (String) -> Unit,
    onHubSelected: (String?) -> Unit,
    onFavoriteHubToggled: (String) -> Unit,
    onTagSelected: (String?) -> Unit,
    onClearFilters: () -> Unit,
) {
    val hubs = remember(state.items) {
        state.items.flatMap { it.hubs }
            .distinctBy { it.id }
            .sortedBy { it.title.lowercase() }
    }
    val tags = remember(state.items, state.favoriteTagIds, state.favoriteTags) {
        val all = (state.favoriteTags.map { (id, title) -> Tag(id, title) } + state.items.flatMap { it.tags })
            .distinctBy { it.id }
        val favorite = all.filter { it.id in state.favoriteTagIds }
        (favorite + all.filterNot { it.id in state.favoriteTagIds })
            .distinctBy { it.id }
            .take(32)
    }
    var streamsExpanded by rememberSaveable { mutableStateOf(false) }
    var hubsExpanded by rememberSaveable { mutableStateOf(false) }
    var tagsExpanded by rememberSaveable { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CollapsibleSectionHeader(
                title = "Потоки",
                summary = "${state.selectedFeedTitle}, ${state.visibleItems.size} статей",
                expanded = streamsExpanded,
                onClick = { streamsExpanded = !streamsExpanded },
            )
            if (streamsExpanded) {
                ChipRow {
                    state.feeds.forEach { feed ->
                        val isActive = feed.id == state.activeFeedId
                        FeedFilterChip(
                            title = if (isActive) "${feed.title} (${state.items.size})" else feed.title,
                            selected = isActive,
                            onClick = { if (feed.id != state.activeFeedId) onFeedSelected(feed.id) },
                        )
                    }
                }
            }

            if (hubs.isNotEmpty()) {
                CollapsibleSectionHeader(
                    title = "Хабы",
                    summary = state.selectedHubId?.let { selected ->
                        hubs.firstOrNull { it.id == selected }?.title ?: "выбран хаб"
                    } ?: "${hubs.size} хабов",
                    expanded = hubsExpanded,
                    onClick = { hubsExpanded = !hubsExpanded },
                )
                if (hubsExpanded) {
                    ChipRow {
                        FeedFilterChip(
                            title = "Все хабы (${state.items.size})",
                            selected = state.selectedHubId == null,
                            onClick = { onHubSelected(null) },
                        )
                        hubs.forEach { hub ->
                            HubChip(
                                hub = hub,
                                count = state.items.count { item -> item.hubs.any { it.id == hub.id } },
                                selected = state.selectedHubId == hub.id,
                                favorite = hub.id in state.favoriteHubIds,
                                onClick = { onHubSelected(hub.id) },
                                onFavoriteClick = { onFavoriteHubToggled(hub.id) },
                            )
                        }
                    }
                }
            }

            if (tags.isNotEmpty()) {
                CollapsibleSectionHeader(
                    title = "Теги",
                    summary = state.selectedTagId?.let { selected ->
                        tags.firstOrNull { it.id == selected }?.title?.let { "#$it" } ?: "выбран тег"
                    } ?: "${tags.size} тегов",
                    expanded = tagsExpanded,
                    onClick = { tagsExpanded = !tagsExpanded },
                )
                if (tagsExpanded) {
                    ChipRow {
                        FeedFilterChip(
                            title = "Все теги (${state.items.size})",
                            selected = state.selectedTagId == null,
                            onClick = { onTagSelected(null) },
                        )
                        tags.forEach { tag ->
                            FeedFilterChip(
                                title = buildString {
                                    if (tag.id in state.favoriteTagIds) append("★ ")
                                    append("#${tag.title}")
                                    append(" (${state.items.count { item -> item.tags.any { it.id == tag.id } }})")
                                },
                                selected = state.selectedTagId == tag.id,
                                onClick = { onTagSelected(tag.id) },
                            )
                        }
                    }
                }
            }

            if (state.activeFilterCount > 0) {
                ChipRow {
                    FeedFilterChip(
                        title = "Сбросить фильтры",
                        selected = false,
                        onClick = onClearFilters,
                    )
                }
            }
        }
    }
}

@Composable
private fun CollapsibleSectionHeader(
    title: String,
    summary: String,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = if (expanded) "Скрыть" else "Показать",
        )
    }
}

@Composable
private fun ChipRow(
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

@Composable
private fun HubChip(
    hub: Hub,
    count: Int,
    selected: Boolean,
    favorite: Boolean,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.widthIn(min = 96.dp, max = 240.dp),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${hub.title} ($count)",
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onClick)
                    .padding(start = 12.dp, top = 9.dp, bottom = 9.dp),
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = onFavoriteClick) {
                Icon(
                    imageVector = if (favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = if (favorite) "Убрать хаб из избранного" else "Добавить хаб в избранное",
                    tint = if (favorite) Color(0xFFFFA000) else MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun FeedFilterChip(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .widthIn(min = 80.dp, max = 220.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
