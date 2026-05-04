package com.arny.habrrss.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arny.habrrss.domain.models.Hub
import com.arny.habrrss.domain.models.Tag
import com.arny.habrrss.presentation.FeedCardMode
import com.arny.habrrss.presentation.FeedSortMode
import com.arny.habrrss.presentation.ReaderUiState
import com.arny.habrrss.presentation.feed.HabrFeedTabState
import com.arny.habrrss.presentation.feed.HabrPublicationSection
import com.arny.habrrss.presentation.feed.habrFeedTabs

@Composable
internal fun FeedFilterBar(
    isWide: Boolean,
    state: ReaderUiState,
    onFeedSelected: (String) -> Unit,
    onPublicationSectionSelected: (HabrPublicationSection) -> Unit,
    onHubSelected: (String?) -> Unit,
    onTagSelected: (String?) -> Unit,
    onClearFilters: () -> Unit,
    onUnreadOnlyChanged: (Boolean) -> Unit,
    onCardModeChanged: (FeedCardMode) -> Unit,
    onSortModeChanged: (FeedSortMode) -> Unit,
) {
    val hubs = remember(state.items, state.favoriteHubIds) {
        val all = state.items.flatMap { it.hubs }.distinctBy { it.id }
        val favorite = all.filter { state.favoriteHubIds.contains(it.id) }
        (favorite + all.filterNot { state.favoriteHubIds.contains(it.id) }).take(8)
    }
    val allTags = remember(state.items) { state.items.flatMap { it.tags }.distinctBy { it.id } }
    val favoriteTags = remember(allTags, state.favoriteTagIds) {
        allTags.filter { state.favoriteTagIds.contains(it.id) }
    }
    val tags = remember(allTags, state.favoriteTagIds) {
        (favoriteTags + allTags.filterNot { state.favoriteTagIds.contains(it.id) }).take(12)
    }
    val regularTags = remember(tags, state.favoriteTagIds) {
        tags.filterNot { state.favoriteTagIds.contains(it.id) }
    }
    val metadataFilterCount = hubs.size + favoriteTags.size + regularTags.size
    val hasMetadataFilters = metadataFilterCount > 0
    var metadataFiltersExpanded by remember(isWide) {
        mutableStateOf(isWide && (state.selectedHubId != null || state.selectedTagId != null))
    }
    val activeMetadataLabel = remember(
        state.selectedHubId,
        state.selectedTagId,
        hubs,
        favoriteTags,
        regularTags,
    ) {
        when {
            state.selectedHubId != null -> {
                hubs.firstOrNull { it.id == state.selectedHubId }?.title?.let { "Хаб: $it" } ?: "Хаб выбран"
            }
            state.selectedTagId != null -> {
                (favoriteTags + regularTags).firstOrNull { it.id == state.selectedTagId }?.title?.let { "Тег: #$it" }
                    ?: "Тег выбран"
            }
            else -> "Метки"
        }
    }

    LaunchedEffect(isWide, state.selectedHubId, state.selectedTagId) {
        if (isWide && (state.selectedHubId != null || state.selectedTagId != null)) {
            metadataFiltersExpanded = true
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 0.dp,
    ) {
        Column {
            Text(
                text = state.selectedFeedTitle,
                modifier = Modifier.padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 12.dp),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                state.habrFeedTabs().forEach { tab ->
                    HabrFeedTab(
                        tab = tab,
                        onClick = {
                            tab.feedId?.let(onFeedSelected) ?: onPublicationSectionSelected(tab.section)
                        },
                    )
                }
            }
            HorizontalDivider()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChipRow {
                    AssistChip(
                        onClick = onClearFilters,
                        label = { Text("Все подряд", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { Icon(Icons.Filled.RssFeed, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        trailingIcon = { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                    FilterChip(
                        selected = state.showUnreadOnly,
                        onClick = { onUnreadOnlyChanged(!state.showUnreadOnly) },
                        label = { Text("Непрочитанные", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { Icon(Icons.Filled.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    )
                    FilterChip(
                        selected = state.feedSortMode == FeedSortMode.Rating,
                        onClick = {
                            onSortModeChanged(if (state.feedSortMode == FeedSortMode.Newest) FeedSortMode.Rating else FeedSortMode.Newest)
                        },
                        label = {
                            Text(
                                if (state.feedSortMode == FeedSortMode.Newest) "Новые" else "Рейтинг",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    )
                    FeedModeChip(state.feedCardMode, onCardModeChanged)
                    if (hasMetadataFilters && !isWide) {
                        Box {
                            FilterChip(
                                selected = state.selectedHubId != null || state.selectedTagId != null,
                                onClick = { metadataFiltersExpanded = true },
                                modifier = Modifier.widthIn(max = 180.dp),
                                label = {
                                    Text(
                                        activeMetadataLabel,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                leadingIcon = { Icon(Icons.Filled.Tag, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Filled.ExpandMore,
                                        contentDescription = "Открыть",
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                            )
                            MetadataDropdownMenu(
                                expanded = metadataFiltersExpanded,
                                onDismiss = { metadataFiltersExpanded = false },
                                hubs = hubs,
                                favoriteTags = favoriteTags,
                                regularTags = regularTags,
                                selectedHubId = state.selectedHubId,
                                selectedTagId = state.selectedTagId,
                                onClear = {
                                    onHubSelected(null)
                                    metadataFiltersExpanded = false
                                },
                                onHubSelected = { hubId ->
                                    onHubSelected(hubId)
                                    metadataFiltersExpanded = false
                                },
                                onTagSelected = { tagId ->
                                    onTagSelected(tagId)
                                    metadataFiltersExpanded = false
                                },
                            )
                        }
                    }
                }
                if (hasMetadataFilters && isWide) {
                    FilterChipRow {
                        AssistChip(
                            onClick = { metadataFiltersExpanded = !metadataFiltersExpanded },
                            label = {
                                Text(
                                    text = if (metadataFiltersExpanded) {
                                        "Скрыть метки и теги"
                                    } else {
                                        "Метки и теги ($metadataFilterCount)"
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            leadingIcon = { Icon(Icons.Filled.Tag, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            trailingIcon = {
                                Icon(
                                    imageVector = if (metadataFiltersExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = if (metadataFiltersExpanded) "Закрыть" else "Открыть",
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                    }
                    if (metadataFiltersExpanded) {
                        if (hubs.isNotEmpty()) {
                            FilterChipRow {
                                hubs.forEach { hub ->
                                    FeedFilterChip(
                                        label = if (state.favoriteHubIds.contains(hub.id)) "* ${hub.title}" else hub.title,
                                        selected = state.selectedHubId == hub.id,
                                    ) { onHubSelected(hub.id) }
                                }
                            }
                        }
                        if (favoriteTags.isNotEmpty() || regularTags.isNotEmpty()) {
                            FilterChipRow {
                                favoriteTags.forEach { tag ->
                                    FeedFilterChip("* #${tag.title}", state.selectedTagId == tag.id) { onTagSelected(tag.id) }
                                }
                                regularTags.forEach { tag ->
                                    FeedFilterChip("#${tag.title}", state.selectedTagId == tag.id) { onTagSelected(tag.id) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetadataDropdownMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    hubs: List<Hub>,
    favoriteTags: List<Tag>,
    regularTags: List<Tag>,
    selectedHubId: String?,
    selectedTagId: String?,
    onClear: () -> Unit,
    onHubSelected: (String) -> Unit,
    onTagSelected: (String) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier
            .widthIn(min = 260.dp, max = 340.dp)
            .heightIn(max = 420.dp),
    ) {
        MetadataMenuItem(
            label = "Все метки и теги",
            selected = selectedHubId == null && selectedTagId == null,
            onClick = onClear,
        )
        if (hubs.isNotEmpty()) {
            HorizontalDivider()
            Text(
                text = "Хабы",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            hubs.forEach { hub ->
                MetadataMenuItem(
                    label = hub.title,
                    selected = selectedHubId == hub.id,
                    onClick = { onHubSelected(hub.id) },
                )
            }
        }
        val tags = favoriteTags + regularTags
        if (tags.isNotEmpty()) {
            HorizontalDivider()
            Text(
                text = "Теги",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            tags.forEach { tag ->
                MetadataMenuItem(
                    label = "#${tag.title}",
                    selected = selectedTagId == tag.id,
                    onClick = { onTagSelected(tag.id) },
                )
            }
        }
    }
}

@Composable
private fun MetadataMenuItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Tag,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        onClick = onClick,
    )
}

@Composable
private fun FilterChipRow(
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

@Composable
private fun HabrFeedTab(
    tab: HabrFeedTabState,
    onClick: () -> Unit,
) {
    val color = if (tab.selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .clickable(enabled = tab.enabled, onClick = onClick)
            .padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = buildString {
                append(tab.section.label)
                tab.count?.let { append(" +$it") }
            },
            style = MaterialTheme.typography.labelLarge,
            color = if (tab.enabled) color else MaterialTheme.colorScheme.outline,
            fontWeight = if (tab.selected) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
        )
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .height(2.dp)
                .width(80.dp)
                .background(if (tab.selected) MaterialTheme.colorScheme.primary else Color.Transparent),
        )
    }
}

@Composable
private fun FeedModeChip(
    mode: FeedCardMode,
    onCardModeChanged: (FeedCardMode) -> Unit,
) {
    FilterChip(
        selected = true,
        onClick = {
            val next = when (mode) {
                FeedCardMode.CompactText -> FeedCardMode.Comfortable
                FeedCardMode.Comfortable -> FeedCardMode.Magazine
                FeedCardMode.Magazine -> FeedCardMode.CompactText
            }
            onCardModeChanged(next)
        },
        label = {
            Text(
                when (mode) {
                    FeedCardMode.CompactText -> "Компактно"
                    FeedCardMode.Comfortable -> "Удобно"
                    FeedCardMode.Magazine -> "Журнал"
                },
            )
        },
    )
}

@Composable
private fun FeedFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = Modifier.widthIn(max = 220.dp),
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Filled.Tag, contentDescription = null, modifier = Modifier.size(16.dp)) },
    )
}
