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
import androidx.compose.ui.tooling.preview.AndroidUiModes.UI_MODE_NIGHT_YES
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.arny.habrrss.data.api.HabrApiSource
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
                        title = "Новые",
                        selected = state.activeFeedId == HabrApiSource.FeedIds.All && !hubSelected,
                        onClick = { onFeedSelected(HabrApiSource.FeedIds.All) },
                    )
                    FeedFilterChip(
                        title = "Все загруженные",
                        selected = state.activeFeedId == HabrApiSource.FeedIds.AllCached && !hubSelected,
                        onClick = { onFeedSelected(HabrApiSource.FeedIds.AllCached) },
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
    if (state.feedPagesCount != null) {
        append(" · стр. ${state.feedPagesLoaded ?: 0}/${state.feedPagesCount}")
    }
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


//region Previews

private fun previewChip(
    id: String,
    title: String,
    count: Int = 0,
    favorite: Boolean = false,
    selected: Boolean = false,
    feedId: String? = null,
): FeedFilterChipState = FeedFilterChipState(
    id = id,
    title = title,
    count = count,
    favorite = favorite,
    selected = selected,
    feedId = feedId,
)

private val previewHubs = listOf(
    previewChip(id = "devops", title = "DevOps", count = 12, favorite = true),
    previewChip(id = "backend", title = "Бэкенд", count = 8),
    previewChip(id = "mobile", title = "Мобильная разработка", count = 5),
    previewChip(id = "infosec", title = "Информационная безопасность", count = 3),
    previewChip(id = "popular", title = "Популярное", count = 21, feedId = "habr-popular"),
)

private val previewTags = listOf(
    previewChip(id = "kotlin", title = "kotlin", count = 6, favorite = true),
    previewChip(id = "android", title = "android", count = 4),
    previewChip(id = "compose", title = "jetpack-compose", count = 3),
    previewChip(id = "rss", title = "rss", count = 1),
)

private fun previewState(
    hubFilters: List<FeedFilterChipState> = emptyList(),
    tagFilters: List<FeedFilterChipState> = emptyList(),
    selectedHubId: String? = null,
    selectedTagId: String? = null,
    searchQuery: String = "",
    showUnreadOnly: Boolean = false,
): ReaderUiState = ReaderUiState(
    hubFilters = hubFilters,
    tagFilters = tagFilters,
    selectedHubId = selectedHubId,
    selectedTagId = selectedTagId,
    searchQuery = searchQuery,
    showUnreadOnly = showUnreadOnly,
)

@Preview(
    name = "Свёрнута · фильтры не активны",
    group = "FeedFilterBar",
    showBackground = true,
)
@Composable
private fun FeedFilterBarCollapsedPreview() {
    FeedFilterBar(
        state = previewState(hubFilters = previewHubs),
        onHubSelected = {},
        onFeedSelected = {},
        onTagSelected = {},
        onClearFilters = {},
    )
}

@Preview(
    name = "Выбраны хаб и тег",
    group = "FeedFilterBar",
    showBackground = true,
    widthDp = 420,
)
@Composable
private fun FeedFilterBarSelectedPreview() {
    FeedFilterBar(
        state = previewState(
            hubFilters = previewHubs.map { it.copy(selected = it.id == "devops") },
            tagFilters = previewTags.map { it.copy(selected = it.id == "kotlin") },
            selectedHubId = "devops",
            selectedTagId = "kotlin",
        ),
        onHubSelected = {},
        onFeedSelected = {},
        onTagSelected = {},
        onClearFilters = {},
    )
}

@Preview(
    name = "Все фильтры активны",
    group = "FeedFilterBar",
    showBackground = true,
    widthDp = 420,
)
@Composable
private fun FeedFilterBarAllFiltersPreview() {
    FeedFilterBar(
        state = previewState(
            hubFilters = previewHubs.map { it.copy(selected = it.id == "backend") },
            tagFilters = previewTags.map { it.copy(selected = it.id == "android") },
            selectedHubId = "backend",
            selectedTagId = "android",
            searchQuery = "compose",
            showUnreadOnly = true,
        ),
        onHubSelected = {},
        onFeedSelected = {},
        onTagSelected = {},
        onClearFilters = {},
    )
}

@Preview(
    name = "Без тегов · подсказка",
    group = "FeedFilterBar",
    showBackground = true,
)
@Composable
private fun FeedFilterBarNoTagsPreview() {
    FeedFilterBar(
        state = previewState(
            hubFilters = previewHubs.map { it.copy(selected = it.id == "infosec") },
            selectedHubId = "infosec",
        ),
        onHubSelected = {},
        onFeedSelected = {},
        onTagSelected = {},
        onClearFilters = {},
    )
}

@Preview(
    name = "Тёмная тема",
    group = "FeedFilterBar",
    showBackground = true,
    uiMode = UI_MODE_NIGHT_YES,
)
@Composable
private fun FeedFilterBarDarkPreview() {
    FeedFilterBar(
        state = previewState(
            hubFilters = previewHubs.map { it.copy(selected = it.id == "devops") },
            tagFilters = previewTags,
            selectedHubId = "devops",
        ),
        onHubSelected = {},
        onFeedSelected = {},
        onTagSelected = {},
        onClearFilters = {},
    )
}

@Preview(
    name = "Крупный шрифт",
    group = "FeedFilterBar",
    showBackground = true,
    fontScale = 1.3f,
)
@Composable
private fun FeedFilterBarLargeFontPreview() {
    FeedFilterBar(
        state = previewState(
            hubFilters = previewHubs,
            tagFilters = previewTags,
        ),
        onHubSelected = {},
        onFeedSelected = {},
        onTagSelected = {},
        onClearFilters = {},
    )
}

//endregion