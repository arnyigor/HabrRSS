package com.arny.habrrss.presentation

import com.arny.habrrss.data.repository.TechReaderRepository
import com.arny.habrrss.domain.models.FeedSettings
import com.arny.habrrss.domain.models.FeedItem
import com.arny.habrrss.domain.usecases.GetFeedsUseCase
import com.arny.habrrss.domain.usecases.OpenArticleUseCase
import com.arny.habrrss.domain.usecases.RefreshFeedUseCase
import com.arny.habrrss.domain.usecases.ToggleBookmarkUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class ReaderPresenter(
    private val repository: TechReaderRepository,
    private val getFeeds: GetFeedsUseCase,
    private val refreshFeed: RefreshFeedUseCase,
    private val openArticle: OpenArticleUseCase,
    private val toggleBookmark: ToggleBookmarkUseCase,
) {
    private val mutableState = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = mutableState

    suspend fun start() {
        if (mutableState.value.items.isNotEmpty()) return
        runLoading {
            val feeds = getFeeds()
            val activeFeedId = feeds.firstOrNull()?.id ?: repository.requireFirstFeedId()
            
            // Try to load from cache first
            val cached = repository.getCachedFeed(activeFeedId)
            if (cached.isNotEmpty()) {
                mutableState.update {
                    it.copy(
                        feeds = feeds,
                        activeFeedId = activeFeedId,
                        items = cached,
                        feedLoading = false,
                    )
                }
            }
            
            // Then refresh from network
            val page = refreshFeed(activeFeedId)
            mutableState.update {
                it.copy(
                    feeds = feeds,
                    activeFeedId = activeFeedId,
                    items = page.items,
                    selectedArticleId = null,
                    article = null,
                    isArticleOpen = false,
                    selectedHubId = null,
                    selectedTagId = null,
                    feedLoading = false,
                    errorMessage = null,
                )
            }
        }
    }

    suspend fun refresh() {
        val feedId = mutableState.value.activeFeedId ?: return
        runLoading {
            val page = refreshFeed(feedId)
            mutableState.update {
                it.copy(
                    items = mergeSelection(page.items),
                    errorMessage = null,
                )
            }
        }
    }

    suspend fun selectFeed(feedId: String) {
        runLoading {
            val page = refreshFeed(feedId)
            mutableState.update {
                it.copy(
                    activeFeedId = feedId,
                    items = page.items,
                    selectedArticleId = null,
                    article = null,
                    isArticleOpen = false,
                    selectedHubId = null,
                    selectedTagId = null,
                    selectedDestination = ReaderDestination.Feed,
                    errorMessage = null,
                )
            }
        }
    }

    suspend fun selectArticle(articleId: String) {
        runLoading {
            val article = openArticle(articleId)
            // Reload cache to get updated read state
            val feedId = mutableState.value.activeFeedId ?: ""
            val items = repository.getCachedFeed(feedId)
            mutableState.update {
                it.copy(
                    selectedArticleId = articleId,
                    article = article,
                    items = items,
                    isArticleOpen = true,
                    selectedDestination = ReaderDestination.Feed,
                    errorMessage = null,
                )
            }
        }
    }

    fun selectDestination(destination: ReaderDestination) {
        mutableState.update {
            it.copy(
                selectedDestination = destination,
                isArticleOpen = if (destination == ReaderDestination.Feed || destination == ReaderDestination.Bookmarks) {
                    it.isArticleOpen
                } else {
                    false
                },
            )
        }
    }

    fun closeArticle() {
        mutableState.update { it.copy(isArticleOpen = false) }
    }

    fun selectHub(hubId: String?) {
        mutableState.update {
            it.copy(
                selectedHubId = if (it.selectedHubId == hubId) null else hubId,
                selectedTagId = null,
                selectedDestination = ReaderDestination.Feed,
                isArticleOpen = false,
            )
        }
    }

    fun selectTag(tagId: String?) {
        mutableState.update {
            it.copy(
                selectedTagId = if (it.selectedTagId == tagId) null else tagId,
                selectedHubId = null,
                selectedDestination = ReaderDestination.Feed,
                isArticleOpen = false,
            )
        }
    }

    fun toggleFavoriteTag(tagId: String) {
        mutableState.update { state ->
            val favoriteTagIds = if (state.favoriteTagIds.contains(tagId)) {
                state.favoriteTagIds - tagId
            } else {
                state.favoriteTagIds + tagId
            }
            state.copy(favoriteTagIds = favoriteTagIds)
        }
    }

    fun updateSearchQuery(query: String) {
        mutableState.update {
            it.copy(
                searchQuery = query,
                selectedHubId = null,
                selectedTagId = null,
                selectedDestination = ReaderDestination.Search,
                isArticleOpen = false,
            )
        }
    }

    fun clearFilters() {
        mutableState.update {
            it.copy(
                selectedHubId = null,
                selectedTagId = null,
                searchQuery = "",
                showUnreadOnly = false,
                isArticleOpen = false,
            )
        }
    }

    fun setShowUnreadOnly(showUnreadOnly: Boolean) {
        mutableState.update {
            it.copy(
                showUnreadOnly = showUnreadOnly,
                isArticleOpen = false,
            )
        }
    }

    fun setFeedCardMode(mode: FeedCardMode) {
        mutableState.update { it.copy(feedCardMode = mode) }
    }

    fun setFeedSortMode(mode: FeedSortMode) {
        mutableState.update { it.copy(feedSortMode = mode) }
    }

    fun updateSettings(transform: (FeedSettings) -> FeedSettings) {
        mutableState.update { it.copy(settings = transform(it.settings)) }
    }

    suspend fun toggleArticleBookmark(articleId: String) {
        toggleBookmark(articleId)
        // Reload from cache
        val feedId = mutableState.value.activeFeedId ?: return
        val items = repository.getCachedFeed(feedId)
        mutableState.update {
            it.copy(items = items)
        }
    }

    private suspend fun runLoading(block: suspend () -> Unit) {
        mutableState.update { it.copy(isRefreshing = true, errorMessage = null) }
        try {
            block()
        } catch (error: Throwable) {
            mutableState.update { it.copy(errorMessage = error.message ?: "Unknown error") }
        } finally {
            mutableState.update { it.copy(isRefreshing = false) }
        }
    }

    private fun mergeSelection(items: List<FeedItem>): List<FeedItem> {
        val selectedId = mutableState.value.selectedArticleId
        return items.map { item ->
            if (item.id == selectedId) item.copy(isRead = true) else item
        }
    }
}
