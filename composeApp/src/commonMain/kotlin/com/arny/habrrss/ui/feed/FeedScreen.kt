package com.arny.habrrss.ui.feed

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.arny.habrrss.presentation.FeedCardMode
import com.arny.habrrss.presentation.FeedSortMode
import com.arny.habrrss.presentation.ReaderUiState
import com.arny.habrrss.presentation.feed.HabrPublicationSection

@Composable
internal fun FeedScreen(
    isWide: Boolean,
    state: ReaderUiState,
    onFeedSelected: (String) -> Unit,
    onPublicationSectionSelected: (HabrPublicationSection) -> Unit,
    onArticleSelected: (String) -> Unit,
    onBookmark: (String) -> Unit,
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
    onLoadMore: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        FeedFilterBar(
            isWide = isWide,
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
            onLoadMore = onLoadMore,
        )
    }
}
