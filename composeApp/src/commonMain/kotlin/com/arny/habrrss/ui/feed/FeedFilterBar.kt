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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arny.habrrss.presentation.FeedFilterChipState
import com.arny.habrrss.presentation.ReaderUiState

@Composable
internal fun FeedFilterBar(
    state: ReaderUiState,
    onHubSelected: (String?) -> Unit,
    onFeedSelected: (String) -> Unit,
    onTagSelected: (String?) -> Unit,
    onClearFilters: () -> Unit,
) {
    val hubs = state.hubFilters
    val tags = state.tagFilters
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
                val hubSelected = hubs.any { it.selected }
                SectionLabel(if (hubSelected) "Хабы · выбранный хаб выделен" else "Хабы")
                ChipRow {
                    FeedFilterChip(
                        title = "Все хабы",
                        selected = !hubSelected,
                        onClick = { onHubSelected(null) },
                    )
                    hubs.forEach { hub ->
                        FeedFilterChip(
                            title = hub.label(prefix = null),
                            selected = hub.selected,
                            onClick = {
                                hub.feedId?.let(onFeedSelected)
                                    ?: onHubSelected(if (hub.selected && state.selectedHubId == null) null else hub.id)
                            },
                        )
                    }
                }
                if (tags.isNotEmpty()) {
                    SectionLabel(if (state.selectedTagId != null) "Теги · выбранный тег выделен" else "Теги")
                    ChipRow {
                        FeedFilterChip(
                            title = "Все теги",
                            selected = state.selectedTagId == null,
                            onClick = { onTagSelected(null) },
                        )
                        tags.forEach { tag ->
                            FeedFilterChip(
                                title = tag.label(prefix = "#"),
                                selected = tag.selected,
                                onClick = { onTagSelected(tag.id) },
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Теги появятся после выбора хаба или добавления тегов в избранное.",
                        modifier = Modifier.padding(horizontal = 20.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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

private fun FeedFilterChipState.label(prefix: String?): String = buildString {
    if (selected) append("✓ ")
    if (favorite) append("★ ")
    if (prefix != null) append(prefix)
    append(title)
    if (count > 0) append(" ($count)")
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
