package com.arny.habrrss.ui.feed

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arny.habrrss.domain.models.FeedItem
import com.arny.habrrss.presentation.FeedCardMode
import com.arny.habrrss.presentation.ReaderUiState
import com.arny.habrrss.presentation.feed.HabrPublicationSection
import com.arny.habrrss.ui.components.EmptyState
import com.arny.habrrss.ui.components.RefreshBox

@Composable
internal fun FeedBody(
    modifier: Modifier,
    state: ReaderUiState,
    selectedArticleId: String?,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onArticleSelected: (String) -> Unit,
    onBookmark: (String) -> Unit,
    onClearFilters: () -> Unit,
    onHubSelected: (String?) -> Unit,
    onLoadMore: () -> Unit,
) {
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
            items = state.visibleItems,
            selectedArticleId = selectedArticleId,
            cardMode = state.feedCardMode,
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            onArticleSelected = onArticleSelected,
            onBookmark = onBookmark,
            onClearFilters = onClearFilters,
            canLoadMore = state.canLoadMore,
            onLoadMore = onLoadMore,
        )
    }
}

@Composable
private fun HabrHubCatalog(
    modifier: Modifier,
    state: ReaderUiState,
    onHubSelected: (String?) -> Unit,
) {
    var hubQuery by remember { mutableStateOf("") }
    val hubs = remember(state.items, hubQuery) {
        val query = hubQuery.trim()
        state.items.flatMap { it.hubs }
            .distinctBy { it.id }
            .filter { query.isBlank() || it.title.contains(query, ignoreCase = true) }
            .sortedBy { it.title.lowercase() }
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            OutlinedTextField(
                value = hubQuery,
                onValueChange = { hubQuery = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                label = { Text("Поиск по хабам") },
                placeholder = { Text("android, kotlin, backend...") },
            )
        }
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

    LaunchedEffect(items.firstOrNull()?.id) {
        if (items.isNotEmpty()) listState.scrollToItem(0)
    }

    // Load more when reaching end of list
    LaunchedEffect(listState, canLoadMore) {
        if (!canLoadMore) return@LaunchedEffect
        snapshotFlow {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            val totalItems = listState.layoutInfo.totalItemsCount
            lastVisibleItem != null && lastVisibleItem.index >= totalItems - 4
        }.collect { shouldLoad ->
            if (shouldLoad) onLoadMore()
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
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }
            }
        }
    }
}
