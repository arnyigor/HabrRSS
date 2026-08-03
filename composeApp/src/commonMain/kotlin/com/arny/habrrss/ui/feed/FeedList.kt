package com.arny.habrrss.ui.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arny.habrrss.domain.models.FeedItem
import com.arny.habrrss.presentation.FeedCardMode
import com.arny.habrrss.presentation.ReaderUiState
import com.arny.habrrss.ui.components.EmptyState
import com.arny.habrrss.ui.components.RefreshBox
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

@Composable
internal fun FeedBody(
    modifier: Modifier,
    state: ReaderUiState,
    selectedArticleId: String?,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onArticleSelected: (String) -> Unit,
    onBookmark: (String) -> Unit,
    onLoadMore: () -> Unit,
) {
    FeedList(
        modifier = modifier,
        items = state.visibleItems,
        selectedArticleId = selectedArticleId,
        cardMode = state.feedCardMode,
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        onArticleSelected = onArticleSelected,
        onBookmark = onBookmark,
        canLoadMore = state.canLoadMore,
        listStateKey = state.feedListStateKey,
        onLoadMore = onLoadMore,
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
    canLoadMore: Boolean = false,
    listStateKey: String = "feed",
    onLoadMore: () -> Unit = {},
) {
    val listState = rememberLazyListState()
    LaunchedEffect(listStateKey) {
        listState.scrollToItem(0)
    }

    // Load more when reaching end of list. Keep it distinct to avoid repeated calls while
    // LazyColumn is remeasuring the same tail items.
    LaunchedEffect(listState, canLoadMore, items.size) {
        if (!canLoadMore) return@LaunchedEffect
        snapshotFlow {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            val totalItems = listState.layoutInfo.totalItemsCount
            val lastIndex = lastVisibleItem?.index ?: -1
            EndOfListSignal(
                lastVisibleIndex = lastIndex,
                totalItems = totalItems,
                shouldLoad = lastIndex >= totalItems - 4,
            )
        }
            .distinctUntilChanged()
            .filter { it.shouldLoad }
            .collect { onLoadMore() }
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
                        message = "В выбранном потоке пока нет загруженных статей.",
                        actionLabel = "Обновить",
                        onAction = onRefresh,
                    )
                }
            }
            items(
                items = items,
                key = { it.id },
                contentType = { "feed_item" },
            ) { item ->
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
                item(key = "load_more", contentType = "load_more") {
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

private data class EndOfListSignal(
    val lastVisibleIndex: Int,
    val totalItems: Int,
    val shouldLoad: Boolean,
)
