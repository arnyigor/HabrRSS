package com.arny.habrrss

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.arny.habrrss.presentation.FeedSortMode
import com.arny.habrrss.presentation.ReaderUiState

@Composable
internal fun FeedScreen(
    isWide: Boolean,
    state: ReaderUiState,
    onFeedSelected: (String) -> Unit,
    onArticleSelected: (String) -> Unit,
    onBookmark: (String) -> Unit,
    onBack: () -> Unit,
    onHubSelected: (String?) -> Unit,
    onTagSelected: (String?) -> Unit,
    onFavoriteTagToggled: (String) -> Unit,
    onClearFilters: () -> Unit,
    onUnreadOnlyChanged: (Boolean) -> Unit,
    onCardModeChanged: (FeedCardMode) -> Unit,
    onSortModeChanged: (FeedSortMode) -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
) {
    if (isWide) {
        Row(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .width(520.dp)
                    .fillMaxHeight(),
            ) {
                FeedFilterBar(
                    state = state,
                    onFeedSelected = onFeedSelected,
                    onHubSelected = onHubSelected,
                    onTagSelected = onTagSelected,
                    onClearFilters = onClearFilters,
                    onUnreadOnlyChanged = onUnreadOnlyChanged,
                    onCardModeChanged = onCardModeChanged,
                    onSortModeChanged = onSortModeChanged,
                )
                FeedList(
                    modifier = Modifier.fillMaxSize(),
                    items = state.visibleItems,
                    selectedArticleId = state.selectedArticleId,
                    cardMode = state.feedCardMode,
                    isRefreshing = state.isRefreshing,
                    onRefresh = onRefresh,
                    onArticleSelected = onArticleSelected,
                    onBookmark = onBookmark,
                    onClearFilters = onClearFilters,
                )
            }
            VerticalDivider(Modifier.fillMaxHeight())
            ArticleScreen(
                modifier = Modifier.weight(1f),
                article = state.article,
                showBack = false,
                settings = state.settings,
                favoriteTagIds = state.favoriteTagIds,
                onBack = onBack,
                onHubSelected = { onHubSelected(it) },
                onTagSelected = { onTagSelected(it) },
                onFavoriteTagToggled = onFavoriteTagToggled,
            )
        }
    } else if (state.isArticleOpen) {
        ArticleScreen(
            modifier = Modifier.fillMaxSize(),
            article = state.article,
            showBack = true,
            settings = state.settings,
            favoriteTagIds = state.favoriteTagIds,
            onBack = onBack,
            onHubSelected = { onHubSelected(it) },
            onTagSelected = { onTagSelected(it) },
            onFavoriteTagToggled = onFavoriteTagToggled,
        )
    } else {
        Column(Modifier.fillMaxSize()) {
            FeedFilterBar(
                state = state,
                onFeedSelected = onFeedSelected,
                onHubSelected = onHubSelected,
                onTagSelected = onTagSelected,
                onClearFilters = onClearFilters,
                onUnreadOnlyChanged = onUnreadOnlyChanged,
                onCardModeChanged = onCardModeChanged,
                onSortModeChanged = onSortModeChanged,
            )
            FeedList(
                modifier = Modifier.fillMaxSize(),
                items = state.visibleItems,
                selectedArticleId = state.selectedArticleId,
                cardMode = state.feedCardMode,
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                onArticleSelected = onArticleSelected,
                onBookmark = onBookmark,
                onClearFilters = onClearFilters,
            )
        }
    }
}

@Composable
internal fun FeedFilterBar(
    state: ReaderUiState,
    onFeedSelected: (String) -> Unit,
    onHubSelected: (String?) -> Unit,
    onTagSelected: (String?) -> Unit,
    onClearFilters: () -> Unit,
    onUnreadOnlyChanged: (Boolean) -> Unit,
    onCardModeChanged: (FeedCardMode) -> Unit,
    onSortModeChanged: (FeedSortMode) -> Unit,
) {
    val hubs = remember(state.items) { state.items.flatMap { it.hubs }.distinctBy { it.id }.take(8) }
    val allTags = remember(state.items) { state.items.flatMap { it.tags }.distinctBy { it.id } }
    val favoriteTags = remember(allTags, state.favoriteTagIds) {
        allTags.filter { state.favoriteTagIds.contains(it.id) }
    }
    val tags = remember(allTags, state.favoriteTagIds) {
        (favoriteTags + allTags.filterNot { state.favoriteTagIds.contains(it.id) }).take(12)
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
                            tab.feedId?.let(onFeedSelected)
                        },
                    )
                }
            }
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AssistChip(
                    onClick = onClearFilters,
                    label = { Text("Все подряд") },
                    leadingIcon = { Icon(Icons.Filled.RssFeed, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
                FilterChip(
                    selected = state.showUnreadOnly,
                    onClick = { onUnreadOnlyChanged(!state.showUnreadOnly) },
                    label = { Text("Непрочитанные") },
                    leadingIcon = { Icon(Icons.Filled.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp)) },
                )
                FilterChip(
                    selected = state.feedSortMode == FeedSortMode.Rating,
                    onClick = {
                        onSortModeChanged(if (state.feedSortMode == FeedSortMode.Newest) FeedSortMode.Rating else FeedSortMode.Newest)
                    },
                    label = { Text(if (state.feedSortMode == FeedSortMode.Newest) "Новые" else "Рейтинг") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, modifier = Modifier.size(16.dp)) },
                )
                FeedModeChip(state.feedCardMode, onCardModeChanged)
                hubs.forEach { hub ->
                    FeedFilterChip(hub.title, state.selectedHubId == hub.id) { onHubSelected(hub.id) }
                }
                favoriteTags.forEach { tag ->
                    FeedFilterChip("* #${tag.title}", state.selectedTagId == tag.id) { onTagSelected(tag.id) }
                }
                tags.forEach { tag ->
                    if (!state.favoriteTagIds.contains(tag.id)) {
                        FeedFilterChip("#${tag.title}", state.selectedTagId == tag.id) { onTagSelected(tag.id) }
                    }
                }
            }
        }
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
        label = { Text(label, maxLines = 1) },
        leadingIcon = { Icon(Icons.Filled.Tag, contentDescription = null, modifier = Modifier.size(16.dp)) },
    )
}

@Composable
internal fun FeedList(
    modifier: Modifier,
    items: List<FeedItem>,
    selectedArticleId: String?,
    cardMode: FeedCardMode,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onArticleSelected: (String) -> Unit,
    onBookmark: (String) -> Unit,
    onClearFilters: () -> Unit,
) {
    val listState = rememberLazyListState()
    RefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (items.isEmpty()) {
                item {
                    EmptyState(
                        title = "Нет публикаций",
                        message = "Ничего не найдено в текущем потоке или фильтрах.",
                        actionLabel = "Сбросить фильтры",
                        onAction = onClearFilters,
                    )
                }
            }
            items(items = items, key = { it.id }) { item ->
                FeedCard(
                    item = item,
                    selected = item.id == selectedArticleId,
                    mode = cardMode,
                    onClick = { onArticleSelected(item.id) },
                    onBookmark = { onBookmark(item.id) },
                )
            }
        }
    }
}

@Composable
private fun FeedCard(
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
                Spacer(Modifier.height(10.dp))
                FeedMetaLine(item)
                if (mode != FeedCardMode.CompactText && item.summary.isNotBlank()) {
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
        humanReadableDate(item.publishedAt).takeIf { it.isNotBlank() }?.let { date ->
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
        modifier = Modifier.horizontalScroll(rememberScrollState()),
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        hubs.forEach { CompactChip(it.title) }
        tags.forEach { CompactChip("#${it.title}") }
    }
}

@Composable
private fun CompactChip(label: String) {
    AssistChip(
        onClick = {},
        label = { Text(label, maxLines = 1) },
    )
}
