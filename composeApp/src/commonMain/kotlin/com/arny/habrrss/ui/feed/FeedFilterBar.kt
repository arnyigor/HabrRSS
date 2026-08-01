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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    onSearchChanged: (String) -> Unit,
    onHubSelected: (String?) -> Unit,
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

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(top = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Лента статей",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onSearchChanged,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    placeholder = { Text("Поиск по статьям, тегам и хабам") },
                )
            }
            ChipRow {
                state.feeds.forEach { feed ->
                    FeedFilterChip(
                        title = feed.title,
                        selected = feed.id == state.activeFeedId,
                        onClick = { if (feed.id != state.activeFeedId) onFeedSelected(feed.id) },
                    )
                }
            }
            if (hubs.isNotEmpty()) {
                Text(
                    text = "Хабы",
                    modifier = Modifier.padding(horizontal = 20.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ChipRow {
                    FeedFilterChip(
                        title = "Все хабы",
                        selected = state.selectedHubId == null,
                        onClick = { onHubSelected(null) },
                    )
                    hubs.forEach { hub ->
                        HubChip(
                            hub = hub,
                            selected = state.selectedHubId == hub.id,
                            onClick = { onHubSelected(hub.id) },
                        )
                    }
                }
            }
            if (tags.isNotEmpty()) {
                Text(
                    text = "Теги",
                    modifier = Modifier.padding(horizontal = 20.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ChipRow {
                    FeedFilterChip(
                        title = "Все теги",
                        selected = state.selectedTagId == null,
                        onClick = { onTagSelected(null) },
                    )
                    tags.forEach { tag ->
                        FeedFilterChip(
                            title = if (tag.id in state.favoriteTagIds) "★ #${tag.title}" else "#${tag.title}",
                            selected = state.selectedTagId == tag.id,
                            onClick = { onTagSelected(tag.id) },
                        )
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
    selected: Boolean,
    onClick: () -> Unit,
) {
    FeedFilterChip(
        title = hub.title,
        selected = selected,
        onClick = onClick,
    )
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
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
