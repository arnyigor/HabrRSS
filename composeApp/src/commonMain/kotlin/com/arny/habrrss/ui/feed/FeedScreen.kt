package com.arny.habrrss.ui.feed

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.arny.habrrss.domain.models.FeedItem
import com.arny.habrrss.domain.models.Hub
import com.arny.habrrss.domain.models.Tag
import com.arny.habrrss.presentation.FeedCardMode
import com.arny.habrrss.presentation.FeedSortMode
import com.arny.habrrss.presentation.ReaderUiState
import com.arny.habrrss.presentation.feed.HabrFeedTabState
import com.arny.habrrss.presentation.feed.HabrPublicationSection
import com.arny.habrrss.presentation.feed.estimatedReadingMinutes
import com.arny.habrrss.presentation.feed.habrCommentsLabel
import com.arny.habrrss.presentation.feed.habrFeedTabs
import com.arny.habrrss.presentation.feed.habrScoreLabel
import com.arny.habrrss.ui.article.ArticleScreen
import com.arny.habrrss.ui.article.FeedThumbnail
import com.arny.habrrss.ui.components.EmptyState
import com.arny.habrrss.ui.components.RefreshBox
import com.arny.habrrss.ui.components.humanReadableDate

@Composable
internal fun FeedScreen(
    isWide: Boolean,
    state: ReaderUiState,
    onFeedSelected: (String) -> Unit,
    onPublicationSectionSelected: (HabrPublicationSection) -> Unit,
    onArticleSelected: (String) -> Unit,
    onBookmark: (String) -> Unit,
    onBack: () -> Unit,
    onHubSelected: (String?) -> Unit,
    onFavoriteHubToggled: (String) -> Unit,
    onTagSelected: (String?) -> Unit,
    onFavoriteTagToggled: (String) -> Unit,
    onClearFilters: () -> Unit,
    onUnreadOnlyChanged: (Boolean) -> Unit,
    onCardModeChanged: (FeedCardMode) -> Unit,
    onSortModeChanged: (FeedSortMode) -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
) {
    if (!isWide && state.isArticleOpen) {
        ArticleScreen(
            modifier = Modifier.fillMaxSize(),
            article = state.article,
            showBack = true,
            settings = state.settings,
            favoriteTagIds = state.favoriteTagIds,
            favoriteHubIds = state.favoriteHubIds,
            onBack = onBack,
            onHubSelected = { onHubSelected(it) },
            onFavoriteHubToggled = onFavoriteHubToggled,
            onTagSelected = { onTagSelected(it) },
            onFavoriteTagToggled = onFavoriteTagToggled,
        )
    } else {
        Column(Modifier.fillMaxSize()) {
            FeedFilterBar(
                state = state,
                onFeedSelected = onFeedSelected,
                onPublicationSectionSelected = onPublicationSectionSelected,
                onHubSelected = onHubSelected,
                onTagSelected = onTagSelected,
                onClearFilters = onClearFilters,
                onUnreadOnlyChanged = onUnreadOnlyChanged,
                onCardModeChanged = onCardModeChanged,
                onSortModeChanged = onSortModeChanged,
            )
            FeedBody(
                modifier = Modifier.fillMaxSize(),
                state = state,
                selectedArticleId = state.selectedArticleId,
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                onArticleSelected = onArticleSelected,
                onBookmark = onBookmark,
                onClearFilters = onClearFilters,
                onHubSelected = onHubSelected,
            )
        }
    }
}

@Composable
internal fun FeedFilterBar(
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
    var metadataFiltersExpanded by remember { mutableStateOf(state.selectedHubId != null || state.selectedTagId != null) }

    LaunchedEffect(state.selectedHubId, state.selectedTagId) {
        if (state.selectedHubId != null || state.selectedTagId != null) {
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
                }
                if (hasMetadataFilters) {
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

@Composable
private fun FeedBody(
    modifier: Modifier,
    state: ReaderUiState,
    selectedArticleId: String?,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onArticleSelected: (String) -> Unit,
    onBookmark: (String) -> Unit,
    onClearFilters: () -> Unit,
    onHubSelected: (String?) -> Unit,
) {
    val computedVisibleItems = remember(
        state.items,
        state.showUnreadOnly,
        state.selectedHubId,
        state.selectedTagId,
        state.searchQuery,
        state.feedSortMode
    ) {
        state.visibleItems
    }

    when (state.selectedPublicationSection) {
        HabrPublicationSection.Hubs -> HabrHubCatalog(
            modifier = modifier,
            state = state,
            onHubSelected = onHubSelected,
        )
        HabrPublicationSection.Articles,
        HabrPublicationSection.Posts,
        HabrPublicationSection.News -> FeedList(
            modifier = modifier,
            items = computedVisibleItems,
            selectedArticleId = selectedArticleId,
            cardMode = state.feedCardMode,
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            onArticleSelected = onArticleSelected,
            onBookmark = onBookmark,
            onClearFilters = onClearFilters,
        )
    }
}

@Composable
private fun HabrHubCatalog(
    modifier: Modifier,
    state: ReaderUiState,
    onHubSelected: (String?) -> Unit,
) {
    val hubs = remember(state.items) {
        state.items.flatMap { it.hubs }.distinctBy { it.id }.sortedBy { it.title.lowercase() }
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (hubs.isEmpty()) {
            item {
                EmptyState(
                    title = "Нет хабов",
                    message = "Хабы появятся после загрузки RSS с параметром with_hubs=true.",
                )
            }
        }
        items(hubs, key = { it.id }) { hub ->
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { onHubSelected(hub.id) },
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(0.dp),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(hub.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "${state.items.count { item -> item.hubs.any { it.id == hub.id } }} публикаций",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
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
    canLoadMore: Boolean = false,
    onLoadMore: () -> Unit = {},
) {
    val listState = rememberLazyListState()

    // Load more when reaching end of list
    LaunchedEffect(listState, canLoadMore) {
        if (!canLoadMore) return@LaunchedEffect

        val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
        val totalItems = listState.layoutInfo.totalItemsCount

        if (lastVisibleItem != null && lastVisibleItem.index >= totalItems - 3) {
            onLoadMore()
        }
    }

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

            // Loading indicator at the bottom when loading more
            if (canLoadMore) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }
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
        modifier = Modifier.widthIn(max = 180.dp),
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    )
}
