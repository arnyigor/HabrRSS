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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
    onHubSelected: (String?) -> Unit,
    onTagSelected: (String?) -> Unit,
    onClearFilters: () -> Unit,
) {
    val selectedHub = remember(state.selectedHubId, state.favoriteHubs, state.favoriteHubTitles, state.items) {
        state.selectedHubId?.let { selectedId ->
            val title = state.favoriteHubTitles[selectedId]
                ?: state.favoriteHubs.firstOrNull { it.first == selectedId }?.second
                ?: state.items.asSequence().flatMap { it.hubs.asSequence() }.firstOrNull { it.id == selectedId }?.title
                ?: selectedId.removePrefix("hub-")
            Hub(selectedId, title)
        }
    }
    val hubs = remember(selectedHub, state.favoriteHubs, state.favoriteHubIds, state.items) {
        val all = (listOfNotNull(selectedHub) +
            state.favoriteHubs.map { (id, title) -> Hub(id, title) } +
            state.items.flatMap { it.hubs })
            .distinctBy { it.id }
            .filterNot { it.title.trim().matches(Regex("-?\\d+")) }
        val active = all.filter { it.id == state.selectedHubId }
        val favorite = all.filter { it.id in state.favoriteHubIds && it.id != state.selectedHubId }
        active + favorite + all.filterNot { it.id in state.favoriteHubIds || it.id == state.selectedHubId }
    }
    val tags = remember(state.items, state.favoriteTagIds, state.favoriteTags, state.favoriteTagTitles, state.selectedTagId) {
        val selectedTag = state.selectedTagId?.let { selectedId ->
            val title = state.favoriteTagTitles[selectedId]
                ?: state.favoriteTags.firstOrNull { it.first == selectedId }?.second
                ?: state.items.asSequence().flatMap { it.tags.asSequence() }.firstOrNull { it.id == selectedId }?.title
                ?: selectedId.removePrefix("tag-")
            Tag(selectedId, title)
        }
        val all = (listOfNotNull(selectedTag) +
            state.favoriteTags.map { (id, title) -> Tag(id, title) } +
            state.items.flatMap { it.tags })
            .distinctBy { it.id }
            .filterNot { it.title.trim().matches(Regex("-?\\d+")) }
        val active = all.filter { it.id == state.selectedTagId }
        val favorite = all.filter { it.id in state.favoriteTagIds && it.id != state.selectedTagId }
        (active + favorite + all.filterNot { it.id in state.favoriteTagIds || it.id == state.selectedTagId })
            .distinctBy { it.id }
            .take(32)
    }
    var filtersExpanded by rememberSaveable { mutableStateOf(state.activeFilterCount > 0) }

    LaunchedEffect(state.selectedHubId, state.selectedTagId) {
        if (state.selectedHubId != null || state.selectedTagId != null) {
            filtersExpanded = true
        }
    }

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
                title = "Фильтры",
                summary = buildFilterSummary(state, hubs.size, tags.size),
                expanded = filtersExpanded,
                onClick = { filtersExpanded = !filtersExpanded },
            )
            if (filtersExpanded) {
                if (hubs.isNotEmpty()) {
                    SectionLabel(if (state.selectedHubId != null) "Хабы · выбранный хаб выделен" else "Хабы")
                    ChipRow {
                        FeedFilterChip(
                            title = "Все хабы",
                            selected = state.selectedHubId == null,
                            onClick = { onHubSelected(null) },
                        )
                        hubs.forEach { hub ->
                            FeedFilterChip(
                                title = buildString {
                                    if (hub.id == state.selectedHubId) append("✓ ")
                                    if (hub.id in state.favoriteHubIds) append("★ ")
                                    append(hub.title)
                                    val count = state.items.count { item -> item.hubs.any { it.id == hub.id } }
                                    if (count > 0) append(" ($count)")
                                },
                                selected = state.selectedHubId == hub.id,
                                onClick = { onHubSelected(hub.id) },
                            )
                        }
                    }
                }
                if (tags.isNotEmpty()) {
                    SectionLabel(if (state.selectedTagId != null) "Теги · выбранный поток выделен" else "Теги")
                    ChipRow {
                        FeedFilterChip(
                            title = "Все теги",
                            selected = state.selectedTagId == null,
                            onClick = { onTagSelected(null) },
                        )
                        tags.forEach { tag ->
                            FeedFilterChip(
                                title = buildString {
                                    if (tag.id == state.selectedTagId) append("✓ ")
                                    if (tag.id in state.favoriteTagIds) append("★ ")
                                    append("#${tag.title}")
                                    val count = state.items.count { item -> item.tags.any { it.id == tag.id } }
                                    if (count > 0) append(" ($count)")
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

private fun buildFilterSummary(
    state: ReaderUiState,
    hubCount: Int,
    tagCount: Int,
): String = buildString {
    append("${state.visibleItems.size} статей")
    if (hubCount > 0) append(" · $hubCount хабов")
    if (tagCount > 0) append(" · $tagCount тегов")
    if (state.activeFilterCount > 0) append(" · фильтров: ${state.activeFilterCount}")
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
private fun SectionLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 20.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
