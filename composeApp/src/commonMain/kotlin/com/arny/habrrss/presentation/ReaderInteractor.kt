package com.arny.habrrss.presentation

import com.arny.habrrss.data.preferences.DefaultPreferencesRepository
import com.arny.habrrss.data.preferences.UserPreferencesRepository
import com.arny.habrrss.data.repository.TechReaderRepository
import com.arny.habrrss.domain.models.FeedKind
import com.arny.habrrss.domain.models.FeedSettings
import com.arny.habrrss.domain.models.FeedItem
import com.arny.habrrss.domain.models.ThemeMode
import com.arny.habrrss.domain.usecases.GetFeedsUseCase
import com.arny.habrrss.domain.usecases.HasMorePagesUseCase
import com.arny.habrrss.domain.usecases.LoadNextPageUseCase
import com.arny.habrrss.domain.usecases.OpenArticleUseCase
import com.arny.habrrss.domain.usecases.RefreshFeedUseCase
import com.arny.habrrss.domain.usecases.ToggleBookmarkUseCase
import com.arny.habrrss.presentation.feed.HabrPublicationSection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

class ReaderInteractor(
    private val repository: TechReaderRepository,
    private val preferencesRepository: UserPreferencesRepository = DefaultPreferencesRepository(),
    private val getFeeds: GetFeedsUseCase,
    private val refreshFeed: RefreshFeedUseCase,
    private val openArticle: OpenArticleUseCase,
    private val toggleBookmark: ToggleBookmarkUseCase,
    private val loadNextPage: LoadNextPageUseCase,
    private val hasMorePages: HasMorePagesUseCase,
) {
    private val mutableState = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = mutableState

    suspend fun start() {
        if (mutableState.value.items.isNotEmpty()) return
        val settings = preferencesRepository.preferences().first()
        val favoriteHubIds = preferencesRepository.favoriteHubIds().first()
        val favoriteTagIds = preferencesRepository.favoriteTagIds().first()
        updateState {
            it.copy(
                settings = settings,
                favoriteHubIds = favoriteHubIds,
                favoriteTagIds = favoriteTagIds,
            )
        }
        runLoading {
            val feeds = getFeeds()
            val activeFeedId = feeds.firstOrNull()?.id ?: repository.requireFirstFeedId()

            // Try to load from cache first
            val cached = repository.getCachedFeed(activeFeedId)
            if (cached.isNotEmpty()) {
                updateState {
                    it.copy(
                        feeds = feeds,
                        activeFeedId = activeFeedId,
                        items = cached,
                        feedLoading = false,
                        canLoadMore = hasMorePages(activeFeedId),
                    )
                }
            }

            // Then refresh from network
            val page = refreshFeed(activeFeedId)
            updateState {
                it.copy(
                    feeds = feeds,
                    activeFeedId = activeFeedId,
                    items = page.items,
                    selectedArticleId = null,
                    selectedArticleBookmarked = false,
                    article = null,
                    isArticleOpen = false,
                    selectedHubId = null,
                    selectedTagId = null,
                    selectedPublicationSection = feeds.firstOrNull { feed -> feed.id == activeFeedId }
                        ?.kind
                        ?.toPublicationSection()
                        ?: HabrPublicationSection.Articles,
                    feedLoading = false,
                    canLoadMore = hasMorePages(activeFeedId),
                    errorMessage = null,
                )
            }
        }
    }

    suspend fun refresh() {
        val feedId = mutableState.value.activeFeedId ?: return
        runLoading {
            val feeds = getFeeds()
            val page = refreshFeed(feedId)
            updateState {
                it.copy(
                    feeds = feeds,
                    items = mergeSelection(page.items),
                    canLoadMore = hasMorePages(feedId),
                    errorMessage = null,
                )
            }
        }
    }

    suspend fun selectFeed(feedId: String) {
        runLoading {
            val page = refreshFeed(feedId)
            updateState {
                it.copy(
                    activeFeedId = feedId,
                    items = page.items,
                    selectedArticleId = null,
                    selectedArticleBookmarked = false,
                    article = null,
                    isArticleOpen = false,
                    selectedHubId = null,
                    selectedTagId = null,
                    searchQuery = "",
                    selectedPublicationSection = it.feeds.firstOrNull { feed -> feed.id == feedId }
                        ?.kind
                        ?.toPublicationSection()
                        ?: HabrPublicationSection.Articles,
                    selectedDestination = ReaderDestination.Feed,
                    canLoadMore = hasMorePages(feedId),
                    errorMessage = null,
                )
            }
        }
    }

    /**
     * Load more items for infinite scroll pagination.
     * Returns true if more items were loaded, false if no more pages.
     */
    suspend fun loadMoreItems(): Boolean {
        val feedId = mutableState.value.activeFeedId ?: return false
        if (!hasMorePages(feedId)) return false

        var loaded = false
        runLoading {
            val nextPage = loadNextPage(feedId)
            if (nextPage != null) {
                updateState { state ->
                    val merged = (state.items + nextPage.items).distinctBy { it.id }
                    state.copy(
                        items = merged,
                        canLoadMore = hasMorePages(feedId),
                        errorMessage = null,
                    )
                }
                loaded = true
            } else {
                updateState { it.copy(canLoadMore = false) }
            }
        }
        return loaded
    }

    suspend fun selectArticle(articleId: String) {
        runLoading {
            val article = openArticle(articleId)
            // Reload cache to get updated read state
            val feedId = mutableState.value.activeFeedId ?: ""
            val items = repository.getCachedFeed(feedId)
            updateState {
                it.copy(
                    selectedArticleId = articleId,
                    selectedArticleBookmarked = items.firstOrNull { item -> item.id == articleId }?.isBookmarked ?: false,
                    article = article,
                    items = items,
                    isArticleOpen = true,
                    selectedDestination = ReaderDestination.Feed,
                    errorMessage = null,
                )
            }
        }
    }

    suspend fun openArticleUrl(url: String) {
        runLoading {
            val article = repository.getArticleByUrl(url)
            updateState {
                it.copy(
                    selectedArticleId = article.id,
                    selectedArticleBookmarked = false,
                    article = article,
                    isArticleOpen = true,
                    selectedDestination = ReaderDestination.Feed,
                    errorMessage = null,
                )
            }
        }
    }

    fun selectDestination(destination: ReaderDestination) {
        updateState {
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
        updateState { it.copy(isArticleOpen = false) }
    }

    fun selectHub(hubId: String?) {
        updateState {
            it.copy(
                selectedHubId = if (it.selectedHubId == hubId) null else hubId,
                selectedTagId = null,
                selectedPublicationSection = HabrPublicationSection.Articles,
                selectedDestination = ReaderDestination.Feed,
                isArticleOpen = false,
            )
        }
    }

    fun selectTag(tagId: String?) {
        updateState {
            it.copy(
                selectedTagId = if (it.selectedTagId == tagId) null else tagId,
                selectedHubId = null,
                selectedPublicationSection = HabrPublicationSection.Articles,
                selectedDestination = ReaderDestination.Feed,
                isArticleOpen = false,
            )
        }
    }

    suspend fun toggleFavoriteTag(tagId: String) {
        var nextIds = emptySet<String>()
        updateState { state ->
            nextIds = if (state.favoriteTagIds.contains(tagId)) {
                state.favoriteTagIds - tagId
            } else {
                state.favoriteTagIds + tagId
            }
            state.copy(favoriteTagIds = nextIds)
        }
        preferencesRepository.setFavoriteTagIds(nextIds)
    }

    suspend fun toggleFavoriteHub(hubId: String) {
        var nextIds = emptySet<String>()
        updateState { state ->
            nextIds = if (state.favoriteHubIds.contains(hubId)) {
                state.favoriteHubIds - hubId
            } else {
                state.favoriteHubIds + hubId
            }
            state.copy(favoriteHubIds = nextIds)
        }
        preferencesRepository.setFavoriteHubIds(nextIds)
    }

    fun selectPublicationSection(section: HabrPublicationSection) {
        updateState {
            it.copy(
                selectedPublicationSection = section,
                selectedDestination = ReaderDestination.Feed,
                isArticleOpen = false,
                selectedHubId = null,
                selectedTagId = null,
                searchQuery = "",
            )
        }
    }

    fun updateSearchQuery(query: String) {
        updateState {
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
        updateState {
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
        updateState {
            it.copy(
                showUnreadOnly = showUnreadOnly,
                isArticleOpen = false,
            )
        }
    }

    fun setFeedCardMode(mode: FeedCardMode) {
        updateState { it.copy(feedCardMode = mode) }
    }

    fun setFeedSortMode(mode: FeedSortMode) {
        updateState { it.copy(feedSortMode = mode) }
    }

    suspend fun updateSettings(transform: (FeedSettings) -> FeedSettings) {
        val current = mutableState.value.settings
        val next = transform(current)
        updateState { it.copy(settings = next) }
        if (next.fontScale != current.fontScale) preferencesRepository.setFontScale(next.fontScale)
        if (next.lineHeightScale != current.lineHeightScale) preferencesRepository.setLineHeightScale(next.lineHeightScale)
        if (next.themeMode != current.themeMode) preferencesRepository.setThemeMode(next.themeMode)
        if (next.compactCards != current.compactCards) preferencesRepository.setCompactCards(next.compactCards)
        if (next.openLinksInsideApp != current.openLinksInsideApp) {
            preferencesRepository.setOpenLinksInsideApp(next.openLinksInsideApp)
        }
    }

    suspend fun toggleArticleBookmark(articleId: String) {
        toggleBookmark(articleId)
        // Reload from cache
        val feedId = mutableState.value.activeFeedId ?: return
        val items = repository.getCachedFeed(feedId)
        updateState {
            val bookmarked = items.firstOrNull { item -> item.id == articleId }?.isBookmarked
                ?: !it.selectedArticleBookmarked
            it.copy(
                items = items,
                selectedArticleBookmarked = if (it.selectedArticleId == articleId) bookmarked else it.selectedArticleBookmarked,
            )
        }
    }

    suspend fun saveCustomFeed(id: String?, title: String, url: String) {
        if (url.isBlank()) return
        repository.upsertCustomFeed(id, title, url)
        val feeds = getFeeds()
        updateState { it.copy(feeds = feeds) }
    }

    suspend fun removeCustomFeed(id: String) {
        repository.removeCustomFeed(id)
        val feeds = getFeeds()
        val activeFeedRemoved = mutableState.value.activeFeedId == id
        updateState {
            it.copy(
                feeds = feeds,
                activeFeedId = if (activeFeedRemoved) feeds.firstOrNull()?.id else it.activeFeedId,
            )
        }
    }

    private suspend fun runLoading(block: suspend () -> Unit) {
        updateState { it.copy(isRefreshing = true, errorMessage = null) }
        try {
            block()
        } catch (e: CancellationException) {
            // Re-throw cancellation to allow proper coroutine cancellation
            updateState { it.copy(isRefreshing = false) }
            throw e
        } catch (e: Exception) {
            updateState { it.copy(errorMessage = e.message ?: "Ошибка загрузки") }
        } finally {
            updateState { it.copy(isRefreshing = false) }
        }
    }

    private inline fun updateState(transform: (ReaderUiState) -> ReaderUiState) {
        mutableState.update { current ->
            val next = transform(current)
            next.copy(visibleItems = computeVisibleItems(next))
        }
    }

    private fun computeVisibleItems(state: ReaderUiState): List<FeedItem> {
        val sectionItems = when (state.selectedDestination) {
            ReaderDestination.Bookmarks -> state.items.filter { it.isBookmarked }
            ReaderDestination.Search -> state.items
            ReaderDestination.Feed,
            ReaderDestination.Sources,
            ReaderDestination.Settings -> state.items
        }
        val terms = state.searchQuery.split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        return sectionItems
            .filter { item -> !state.showUnreadOnly || !item.isRead }
            .filter { item -> state.selectedHubId == null || item.hubs.any { it.id == state.selectedHubId } }
            .filter { item -> state.selectedTagId == null || item.tags.any { it.id == state.selectedTagId } }
            .filter { item -> terms.all { term -> item.matchesSearchTerm(term) } }
            .let { filtered ->
                when (state.feedSortMode) {
                    FeedSortMode.Newest -> filtered.sortedByDescending { it.publishedAtEpoch ?: 0L }
                    FeedSortMode.Rating -> filtered.sortedByDescending {
                        it.rating?.filter { char -> char.isDigit() || char == '-' }?.toIntOrNull() ?: 0
                    }
                }
            }
    }

    private fun FeedItem.matchesSearchTerm(rawTerm: String): Boolean {
        val term = rawTerm.removePrefix("#")
        val searchableText = listOf(
            title,
            summary,
            descriptionHtml.orEmpty(),
            author?.displayName.orEmpty(),
            tags.joinToString(" ") { "${it.title} ${it.id}" },
            hubs.joinToString(" ") { "${it.title} ${it.id}" },
        ).joinToString(" ")
        return searchableText.contains(term, ignoreCase = true)
    }

    private fun mergeSelection(items: List<FeedItem>): List<FeedItem> {
        val selectedId = mutableState.value.selectedArticleId
        return items.map { item ->
            if (item.id == selectedId) item.copy(isRead = true) else item
        }
    }

    private fun FeedKind.toPublicationSection(): HabrPublicationSection = when (this) {
        FeedKind.All,
        FeedKind.Best,
        FeedKind.Hub,
        FeedKind.Tag,
        FeedKind.Search,
        FeedKind.Custom -> HabrPublicationSection.Articles
        FeedKind.Posts -> HabrPublicationSection.Posts
        FeedKind.News -> HabrPublicationSection.News
    }
}
