package com.arny.habrrss

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import com.arny.habrrss.presentation.FeedCardMode
import com.arny.habrrss.presentation.FeedSortMode
import com.arny.habrrss.presentation.ReaderDestination
import com.arny.habrrss.presentation.ReaderPresenter
import com.arny.habrrss.presentation.ReaderUiState
import kotlinx.coroutines.launch

@Composable
internal fun ReaderApp(
    state: ReaderUiState,
    presenter: ReaderPresenter,
) {
    val scope = rememberCoroutineScope()
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeContentPadding(),
    ) {
        val isWide = maxWidth >= WideLayoutMinWidth
        val openArticle: (String) -> Unit = { articleId ->
            scope.launch { presenter.selectArticle(articleId) }
        }

        PlatformBackHandler(enabled = !isWide && state.isArticleOpen) {
            presenter.closeArticle()
        }

        if (isWide) {
            Row(Modifier.fillMaxSize()) {
                ReaderRail(state = state, onDestinationSelected = presenter::selectDestination)
                MainContent(
                    modifier = Modifier.weight(1f),
                    isWide = true,
                    state = state,
                    onRefresh = { scope.launch { presenter.refresh() } },
                    onFeedSelected = { feedId -> scope.launch { presenter.selectFeed(feedId) } },
                    onArticleSelected = openArticle,
                    onBookmark = { id -> scope.launch { presenter.toggleArticleBookmark(id) } },
                    onBack = presenter::closeArticle,
                    onHubSelected = presenter::selectHub,
                    onTagSelected = presenter::selectTag,
                    onFavoriteTagToggled = presenter::toggleFavoriteTag,
                    onSearchChanged = presenter::updateSearchQuery,
                    onClearFilters = presenter::clearFilters,
                    onUnreadOnlyChanged = presenter::setShowUnreadOnly,
                    onCardModeChanged = presenter::setFeedCardMode,
                    onSortModeChanged = presenter::setFeedSortMode,
                    onFontScaleChanged = { value ->
                        presenter.updateSettings { it.copy(fontScale = value) }
                    },
                    onLineHeightChanged = { value ->
                        presenter.updateSettings { it.copy(lineHeightScale = value) }
                    },
                    onOpenLinksInsideChanged = { value ->
                        presenter.updateSettings { it.copy(openLinksInsideApp = value) }
                    },
                    onRetry = { scope.launch { presenter.refresh() } },
                )
            }
        } else {
            Scaffold(
                bottomBar = {
                    if (!state.isArticleOpen) {
                        ReaderBottomBar(state = state, onDestinationSelected = presenter::selectDestination)
                    }
                },
            ) { innerPadding ->
                MainContent(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize(),
                    isWide = false,
                    state = state,
                    onRefresh = { scope.launch { presenter.refresh() } },
                    onFeedSelected = { feedId -> scope.launch { presenter.selectFeed(feedId) } },
                    onArticleSelected = openArticle,
                    onBookmark = { id -> scope.launch { presenter.toggleArticleBookmark(id) } },
                    onBack = presenter::closeArticle,
                    onHubSelected = presenter::selectHub,
                    onTagSelected = presenter::selectTag,
                    onFavoriteTagToggled = presenter::toggleFavoriteTag,
                    onSearchChanged = presenter::updateSearchQuery,
                    onClearFilters = presenter::clearFilters,
                    onUnreadOnlyChanged = presenter::setShowUnreadOnly,
                    onCardModeChanged = presenter::setFeedCardMode,
                    onSortModeChanged = presenter::setFeedSortMode,
                    onFontScaleChanged = { value ->
                        presenter.updateSettings { it.copy(fontScale = value) }
                    },
                    onLineHeightChanged = { value ->
                        presenter.updateSettings { it.copy(lineHeightScale = value) }
                    },
                    onOpenLinksInsideChanged = { value ->
                        presenter.updateSettings { it.copy(openLinksInsideApp = value) }
                    },
                    onRetry = { scope.launch { presenter.refresh() } },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainContent(
    modifier: Modifier,
    isWide: Boolean,
    state: ReaderUiState,
    onRefresh: () -> Unit,
    onFeedSelected: (String) -> Unit,
    onArticleSelected: (String) -> Unit,
    onBookmark: (String) -> Unit,
    onBack: () -> Unit,
    onHubSelected: (String?) -> Unit,
    onTagSelected: (String?) -> Unit,
    onFavoriteTagToggled: (String) -> Unit,
    onSearchChanged: (String) -> Unit,
    onClearFilters: () -> Unit,
    onUnreadOnlyChanged: (Boolean) -> Unit,
    onCardModeChanged: (FeedCardMode) -> Unit,
    onSortModeChanged: (FeedSortMode) -> Unit,
    onFontScaleChanged: (Float) -> Unit,
    onLineHeightChanged: (Float) -> Unit,
    onOpenLinksInsideChanged: (Boolean) -> Unit,
    onRetry: () -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {
        if (!state.isArticleOpen || isWide) {
            ReaderTopBar(state = state, onRefresh = onRefresh)
        }
        state.errorMessage?.let { ErrorBanner(it, onRetry = onRetry) }

        when (state.selectedDestination) {
            ReaderDestination.Sources -> SourceScreen(state.feeds, state.activeFeedId, onFeedSelected)
            ReaderDestination.Search -> SearchScreen(
                state = state,
                onSearchChanged = onSearchChanged,
                onArticleSelected = onArticleSelected,
                onBookmark = onBookmark,
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh,
            )
            ReaderDestination.Settings -> SettingsScreen(
                state = state,
                onCardModeChanged = onCardModeChanged,
                onFontScaleChanged = onFontScaleChanged,
                onLineHeightChanged = onLineHeightChanged,
                onOpenLinksInsideChanged = onOpenLinksInsideChanged,
                onFavoriteTagToggled = onFavoriteTagToggled,
            )
            ReaderDestination.Bookmarks,
            ReaderDestination.Feed -> FeedScreen(
                isWide = isWide,
                state = state,
                onFeedSelected = onFeedSelected,
                onArticleSelected = onArticleSelected,
                onBookmark = onBookmark,
                onBack = onBack,
                onHubSelected = onHubSelected,
                onTagSelected = onTagSelected,
                onFavoriteTagToggled = onFavoriteTagToggled,
                onClearFilters = onClearFilters,
                onUnreadOnlyChanged = onUnreadOnlyChanged,
                onCardModeChanged = onCardModeChanged,
                onSortModeChanged = onSortModeChanged,
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh,
            )
        }
    }
}
