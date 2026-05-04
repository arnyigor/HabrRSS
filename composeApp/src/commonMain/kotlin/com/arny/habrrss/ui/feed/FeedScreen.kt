package com.arny.habrrss.ui.feed

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.arny.habrrss.presentation.FeedCardMode
import com.arny.habrrss.presentation.FeedSortMode
import com.arny.habrrss.presentation.ReaderUiState
import com.arny.habrrss.presentation.feed.HabrPublicationSection
import com.arny.habrrss.ui.article.ArticleScreen

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
            )
        }
    }
}
