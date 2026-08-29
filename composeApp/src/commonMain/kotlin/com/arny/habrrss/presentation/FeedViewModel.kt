package com.arny.habrrss.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.habrrss.core.logging.AppLog
import com.arny.habrrss.data.api.HabrApiSource
import com.arny.habrrss.data.preferences.UserPreferencesRepository
import com.arny.habrrss.data.remote.habr.error.HabrRemoteException
import com.arny.habrrss.data.repository.TechReaderRepository
import com.arny.habrrss.domain.models.FeedDescriptor
import com.arny.habrrss.domain.models.FeedItem
import com.arny.habrrss.domain.models.FeedKind
import com.arny.habrrss.domain.models.FeedSettings
import com.arny.habrrss.domain.models.Hub
import com.arny.habrrss.presentation.feed.HabrPublicationSection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

sealed interface FeedIntent {
    data object Refresh : FeedIntent
    data object LoadMore : FeedIntent
    data object LoadAllPages : FeedIntent
    data object CancelLoadAllPages : FeedIntent
    data object DismissError : FeedIntent
    data class SelectFeed(val feedId: String) : FeedIntent
    data class SelectDestination(val destination: ReaderDestination) : FeedIntent
    data class SelectArticle(val articleId: String) : FeedIntent
    data object CloseArticle : FeedIntent
    data class SelectHub(val hubId: String?, val title: String? = null) : FeedIntent
    data class SelectTag(val tagId: String?, val title: String? = null) : FeedIntent
    data class ToggleFavoriteTag(val tagId: String) : FeedIntent
    data class ToggleFavoriteHub(val hubId: String) : FeedIntent
    data class SelectPublicationSection(val section: HabrPublicationSection) : FeedIntent
    data class UpdateSearchQuery(val query: String) : FeedIntent
    data object ClearFilters : FeedIntent
    data class SetShowUnreadOnly(val showUnreadOnly: Boolean) : FeedIntent
    data class SetFeedCardMode(val mode: FeedCardMode) : FeedIntent
    data class SetFeedSortMode(val mode: FeedSortMode) : FeedIntent
    data class UpdateSettings(val transform: (FeedSettings) -> FeedSettings) : FeedIntent
    data class SaveCustomFeed(val id: String?, val title: String, val url: String) : FeedIntent
    data class RemoveCustomFeed(val id: String) : FeedIntent
    data class OpenArticleUrl(val url: String) : FeedIntent
    data class OpenHubFeed(val slug: String, val title: String) : FeedIntent
    data class ToggleArticleBookmark(val articleId: String) : FeedIntent
}

private data class FavoriteMetadata(
    val hubIds: Set<String>,
    val tagIds: Set<String>,
    val hubTitles: Map<String, String>,
    val tagTitles: Map<String, String>,
)

class FeedViewModel(
    private val repository: TechReaderRepository,
    private val preferencesRepository: UserPreferencesRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = mutableState

    private var feedJob: Job? = null
    private var bookmarksJob: Job? = null
    private var localStateJob: Job? = null
    private var isLoadingNextPage = false
    private var loadAllPagesJob: Job? = null
    // Local "Все загруженные" paging cursor. The DB-backed archive is browsed page by page, so
    // opening it does not map the whole cache to domain objects at once.
    private var localAllOffset = 0
    private var localAllHasMore = false
    // Memoization for the expensive derived UI data (visibleItems + filter chips). The cache key
    // is derived from the inputs of computeVisibleItems/withFilterChips, so unrelated state updates
    // (progress ticks, scroll requests, ...) do not re-scan the whole item list on every change.
    private var derivedCacheKey: String? = null
    private var derivedCache: DerivedFeedData? = null

    init {
        viewModelScope.launch(Dispatchers.Default) { start() }
    }

    fun dispatch(intent: FeedIntent) {
        AppLog.d(TAG, "dispatch ${intent.logSummary()}")
        when (intent) {
            FeedIntent.Refresh -> refresh()
            FeedIntent.LoadMore -> loadMoreItems()
            FeedIntent.LoadAllPages -> loadAllPages()
            FeedIntent.CancelLoadAllPages -> cancelLoadAllPages()
            FeedIntent.DismissError -> dismissError()
            is FeedIntent.SelectFeed -> selectFeed(intent.feedId)
            is FeedIntent.SelectDestination -> selectDestination(intent.destination)
            is FeedIntent.SelectArticle -> selectArticle(intent.articleId)
            FeedIntent.CloseArticle -> closeArticle()
            is FeedIntent.SelectHub -> selectHub(intent.hubId, intent.title)
            is FeedIntent.SelectTag -> selectTag(intent.tagId, intent.title)
            is FeedIntent.ToggleFavoriteTag -> toggleFavoriteTag(intent.tagId)
            is FeedIntent.ToggleFavoriteHub -> toggleFavoriteHub(intent.hubId)
            is FeedIntent.SelectPublicationSection -> selectPublicationSection(intent.section)
            is FeedIntent.UpdateSearchQuery -> updateSearchQuery(intent.query)
            FeedIntent.ClearFilters -> clearFilters()
            is FeedIntent.SetShowUnreadOnly -> setShowUnreadOnly(intent.showUnreadOnly)
            is FeedIntent.SetFeedCardMode -> setFeedCardMode(intent.mode)
            is FeedIntent.SetFeedSortMode -> setFeedSortMode(intent.mode)
            is FeedIntent.UpdateSettings -> updateSettings(intent.transform)
            is FeedIntent.SaveCustomFeed -> saveCustomFeed(intent.id, intent.title, intent.url)
            is FeedIntent.RemoveCustomFeed -> removeCustomFeed(intent.id)
            is FeedIntent.OpenArticleUrl -> openArticleUrl(intent.url)
            is FeedIntent.OpenHubFeed -> openHubFeed(intent.slug, intent.title)
            is FeedIntent.ToggleArticleBookmark -> toggleArticleBookmark(intent.articleId)
        }
    }

    private suspend fun start() {
        val startedAt = Clock.System.now().toEpochMilliseconds()
        AppLog.i(TAG, "start")
        val settings = preferencesRepository.preferences().first()
        migrateFavoriteMetadataFromPreferences()
        val feeds = repository.getFeeds()
        val activeFeedId = feeds.firstOrNull()?.id ?: repository.requireFirstFeedId()
        updateState {
            it.copy(
                settings = settings,
                feedCardMode = settings.toFeedCardMode(),
                feeds = feeds,
                activeFeedId = activeFeedId,
                selectedPublicationSection = feeds.firstOrNull { feed -> feed.id == activeFeedId }
                    ?.kind
                    ?.toPublicationSection()
                    ?: HabrPublicationSection.Articles,
                favoriteHubIds = repository.getFavoriteHubIds(),
                favoriteTagIds = repository.getFavoriteTagIds(),
                favoriteHubTitles = repository.getFavoriteHubTitles(),
                favoriteTagTitles = repository.getFavoriteTagTitles(),
            )
        }
        observeFeed(activeFeedId)
        observeBookmarks()
        observeLocalFavorites()
        refresh(force = false)
        AppLog.i(
            TAG,
            "start completed feed=$activeFeedId feeds=${feeds.size} elapsed=${
                Clock.System.now().toEpochMilliseconds() - startedAt
            }ms"
        )
    }

    private suspend fun migrateFavoriteMetadataFromPreferences() {
        val currentHubIds = repository.getFavoriteHubIds()
        val currentTagIds = repository.getFavoriteTagIds()
        preferencesRepository.favoriteHubIds().first()
            .filterNot { it in currentHubIds }
            .forEach { repository.toggleFavoriteHub(it) }
        preferencesRepository.favoriteTagIds().first()
            .filterNot { it in currentTagIds }
            .forEach { repository.toggleFavoriteTag(it) }
    }

    private fun observeFeed(feedId: String) {
        AppLog.i(TAG, "observeFeed feedId=$feedId")
        feedJob?.cancel()
        // A pending load-more (e.g. a local archive page request) must not leak its in-flight flag
        // into the newly observed feed, otherwise loadMoreItems() stays blocked forever.
        isLoadingNextPage = false
        if (feedId == HabrApiSource.FeedIds.AllCached) {
            localAllOffset = 0
            localAllHasMore = false
        }
        feedJob = viewModelScope.launch(Dispatchers.Default) {
            if (feedId == HabrApiSource.FeedIds.AllCached) {
                observeLocalAllPageFlow(feedId)
            } else {
                repository.observeFeed(feedId).flowOn(Dispatchers.Default).collect { items ->
                    AppLog.d(TAG, "observeFeed emission feedId=$feedId items=${items.size}")
                    updateState { state ->
                        val selected = state.selectedArticleId
                        state.copy(
                            items = items,
                            localAllTotalCount = null,
                            selectedArticleBookmarked = items.firstOrNull { it.id == selected }?.isBookmarked
                                ?: state.selectedArticleBookmarked,
                        )
                    }
                }
            }
        }
    }

    /**
     * Collects one page of the local "Все загруженные" archive (plus the current SQL filters).
     * Pages are appended to the already loaded items, except the first page which replaces them.
     */
    private suspend fun observeLocalAllPageFlow(feedId: String) {
        val filters = mutableState.value
        val hubFilter = filters.selectedHubId
        val tagFilter = filters.selectedTagId
        val query = filters.searchQuery.takeIf { it.isNotBlank() }
        val hideRead = filters.showUnreadOnly
        val count = repository.countLocalAll(hubFilter, tagFilter, query, hideRead)
        localAllHasMore = localAllOffset + LOCAL_ALL_PAGE_SIZE < count
        AppLog.i(
            TAG,
            "observeLocalAllPage feedId=$feedId offset=$localAllOffset count=$count hasMore=$localAllHasMore " +
                "hub=$hubFilter tag=$tagFilter query=$query unread=$hideRead"
        )
        updateState {
            it.copy(
                canLoadMore = localAllHasMore,
                localAllTotalCount = count,
                errorMessage = null,
            )
        }
        repository.observeLocalAllPage(
            limit = LOCAL_ALL_PAGE_SIZE,
            offset = localAllOffset,
            hubFilter = hubFilter,
            tagFilter = tagFilter,
            query = query,
            hideRead = hideRead,
        ).flowOn(Dispatchers.Default).collect { items ->
            AppLog.d(TAG, "observeLocalAllPage emission feedId=$feedId offset=$localAllOffset items=${items.size}")
            updateState { state ->
                val merged = if (localAllOffset == 0) {
                    items
                } else {
                    (state.items + items).distinctBy { it.articleIdentityKey() }
                }
                state.copy(items = merged)
            }
            // The page stream is long-lived (it stays subscribed until the feed job is cancelled),
            // so the load-more flag must be released right after the page has been applied. It is
            // intentionally NOT reset in a finally block of loadMoreLocalAll: the previous page job
            // can be cancelled asynchronously while the next one already runs, and resetting there
            // would race with the new page (allowing a duplicate page request).
            isLoadingNextPage = false
        }
    }

    private fun observeBookmarks() {
        if (bookmarksJob != null) return
        bookmarksJob = viewModelScope.launch(Dispatchers.Default) {
            repository.observeBookmarks().flowOn(Dispatchers.Default).collect { bookmarkedItems ->
                updateState { it.copy(bookmarkedItems = bookmarkedItems) }
            }
        }
    }

    private fun observeLocalFavorites() {
        if (localStateJob != null) return
        localStateJob = viewModelScope.launch(Dispatchers.Default) {
            kotlinx.coroutines.flow.combine(
                repository.observeFavoriteHubIds(),
                repository.observeFavoriteTagIds(),
                repository.observeFavoriteHubTitles(),
                repository.observeFavoriteTagTitles(),
            ) { hubIds, tagIds, hubTitles, tagTitles ->
                FavoriteMetadata(hubIds, tagIds, hubTitles, tagTitles)
            }
                .flowOn(Dispatchers.Default)
                .collect { metadata ->
                    updateState {
                        it.copy(
                            favoriteHubIds = metadata.hubIds,
                            favoriteTagIds = metadata.tagIds,
                            favoriteHubTitles = metadata.hubTitles,
                            favoriteTagTitles = metadata.tagTitles,
                        )
                    }
                }
        }
    }

    private fun resetPager(feedId: String) {
        // Paging 3 was removed: the UI renders a DB-backed Flow and requests pages via
        // repository.loadNextPage. Kept as a no-op hook so feed switches reset nothing stale.
        AppLog.d(TAG, "resetPager feedId=$feedId (no-op, paging via repository cursor)")
    }

    fun refresh(force: Boolean = true, scrollToTopOnNewItems: Boolean = true) {
        val feedId = mutableState.value.activeFeedId ?: return
        val previousTopArticle = mutableState.value
            .takeIf { scrollToTopOnNewItems && it.selectedDestination == ReaderDestination.Feed }
            ?.items
            ?.firstOrNull()
            ?.articleIdentityKey()
        viewModelScope.launch(Dispatchers.Default) {
            var refreshed = false
            runLoading {
                AppLog.i(TAG, "refresh start feedId=$feedId force=$force")
                repository.getFeeds(forceRefresh = true)
                    .also { feeds -> updateState { it.copy(feeds = feeds) } }
                val page = repository.refreshFeed(feedId, force = force)
                requestScrollToTopAfterFreshLoad(
                    feedId = feedId,
                    previousTopArticle = previousTopArticle,
                    loadedItems = page.items,
                )
                // For "Все загруженные" canLoadMore is driven exclusively by the paged local
                // archive flow (repository.hasMorePages returns false for it by design); writing
                // it here would race the flow and could leave pagination disabled.
                updateState {
                    if (feedId == HabrApiSource.FeedIds.AllCached) {
                        it.copy(errorMessage = null)
                    } else {
                        it.copy(
                            canLoadMore = repository.hasMorePages(feedId),
                            errorMessage = null
                        )
                    }
                }
                AppLog.i(
                    TAG,
                    "refresh completed feedId=$feedId canLoadMore=${
                        if (feedId == HabrApiSource.FeedIds.AllCached) {
                            localAllHasMore
                        } else {
                            repository.hasMorePages(feedId)
                        }
                    }"
                )
                refreshed = true
            }
        }
    }

    private fun requestScrollToTopAfterFreshLoad(
        feedId: String,
        previousTopArticle: String?,
        loadedItems: List<FeedItem>,
    ) {
        val nextTopArticle = loadedItems.firstOrNull()?.articleIdentityKey()
        if (previousTopArticle == null || nextTopArticle == null || previousTopArticle == nextTopArticle) {
            return
        }
        updateState { state ->
            if (
                state.activeFeedId == feedId &&
                state.selectedDestination == ReaderDestination.Feed &&
                !state.isArticleOpen
            ) {
                AppLog.i(TAG, "request feed scroll to top feedId=$feedId previous=$previousTopArticle next=$nextTopArticle")
                state.copy(feedScrollToTopRequest = state.feedScrollToTopRequest + 1)
            } else {
                state
            }
        }
    }

    private fun ReaderUiState.requestFeedScrollToTop(): ReaderUiState =
        if (selectedDestination == ReaderDestination.Feed && !isArticleOpen) {
            copy(feedScrollToTopRequest = feedScrollToTopRequest + 1)
        } else {
            this
        }

    fun selectFeed(feedId: String) {
        viewModelScope.launch(Dispatchers.Default) {
            AppLog.i(TAG, "selectFeed feedId=$feedId")
            cancelLoadAllPagesOnFeedSwitch()
            observeFeed(feedId)
            resetPager(feedId)
            updateState { state ->
                state.copy(
                    activeFeedId = feedId,
                    selectedArticleId = null,
                    selectedArticleBookmarked = false,
                    article = null,
                    isArticleOpen = false,
                    selectedHubId = null,
                    selectedHubTitle = null,
                    selectedTagId = null,
                    selectedTagTitle = null,
                    searchQuery = "",
                    selectedPublicationSection = state.feeds.firstOrNull { feed -> feed.id == feedId }
                        ?.kind
                        ?.toPublicationSection()
                        ?: HabrPublicationSection.Articles,
                    selectedDestination = ReaderDestination.Feed,
                    canLoadMore = if (feedId == HabrApiSource.FeedIds.AllCached) {
                        localAllHasMore
                    } else {
                        repository.hasMorePages(feedId)
                    },
                    errorMessage = null,
                )
                    .requestFeedScrollToTop()
            }
            refresh(force = false, scrollToTopOnNewItems = false)
        }
    }

    fun selectArticle(articleId: String) {
        AppLog.i(TAG, "selectArticle articleId=$articleId")
        updateState { state ->
            state.copy(
                selectedArticleId = articleId,
                selectedArticleBookmarked = state.items.firstOrNull { it.id == articleId }?.isBookmarked
                    ?: state.bookmarkedItems.firstOrNull { it.id == articleId }?.isBookmarked
                    ?: false,
                isArticleOpen = true,
                selectedDestination = when (state.selectedDestination) {
                    ReaderDestination.Bookmarks,
                    ReaderDestination.Search -> state.selectedDestination

                    ReaderDestination.Feed,
                    ReaderDestination.Sources,
                    ReaderDestination.Settings -> ReaderDestination.Feed
                },
            )
        }
    }

    fun loadMoreItems() {
        val feedId = mutableState.value.activeFeedId ?: return
        if (isLoadingNextPage || loadAllPagesJob?.isActive == true) return
        if (feedId == HabrApiSource.FeedIds.AllCached) {
            loadMoreLocalAll()
            return
        }
        if (!repository.hasMorePages(feedId)) return
        viewModelScope.launch(Dispatchers.Default) {
            isLoadingNextPage = true
            try {
                AppLog.i(TAG, "loadMore start feedId=$feedId")
                var page = repository.loadNextPage(feedId)
                var loaded = page?.items.orEmpty()
                // A page can contribute no items after URL-based dedup or hub filtering while the
                // cursor still points to more pages. Keep advancing so pagination does not stall
                // silently at the end of the list.
                while (page != null && page.items.isEmpty() && repository.hasMorePages(feedId)) {
                    page = repository.loadNextPage(feedId)
                    loaded = page?.items.orEmpty()
                }
                if (page != null) {
                    AppLog.i(
                        TAG,
                        "loadMore page feedId=$feedId items=${loaded.size} canLoadMore=${repository.hasMorePages(feedId)}"
                    )
                    updateState {
                        it.copy(
                            canLoadMore = repository.hasMorePages(feedId),
                            errorMessage = null
                        )
                    }
                } else {
                    updateState { it.copy(canLoadMore = false) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateState { it.copy(errorMessage = e.message ?: "Ошибка загрузки") }
            } finally {
                isLoadingNextPage = false
            }
        }
    }

    /**
     * Advances the "Все загруженные" cursor by one page and restarts the observation at the new
     * offset. The previous collector is cancelled so only one page stream is active at a time.
     *
     * [isLoadingNextPage] is released inside [observeLocalAllPageFlow] right after the page has
     * been applied (never in a finally here): the cancelled previous job can outlive this method
     * and would otherwise race the flag with the new page stream.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun loadMoreLocalAll() {
        if (!localAllHasMore || isLoadingNextPage) return
        val feedId = HabrApiSource.FeedIds.AllCached
        AppLog.i(TAG, "loadMoreLocalAll offset=$localAllOffset hasMore=$localAllHasMore")
        isLoadingNextPage = true
        feedJob?.cancel()
        feedJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                localAllOffset += LOCAL_ALL_PAGE_SIZE
                observeLocalAllPageFlow(feedId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w(TAG, "loadMoreLocalAll failed offset=$localAllOffset", e)
                isLoadingNextPage = false
                updateState { it.copy(errorMessage = e.message ?: "Ошибка загрузки") }
            }
        }
    }

    /**
     * Restarts paged observation of "Все загруженные" after a filter change (tag/search/unread).
     * SQL filters are pushed to the DAO, so the page stream already returns only matching rows.
     */
    private fun reobserveAllCachedIfActive() {
        if (mutableState.value.activeFeedId == HabrApiSource.FeedIds.AllCached) {
            observeFeed(HabrApiSource.FeedIds.AllCached)
        }
    }

    /**
     * Loads every remaining page of the active hub archive in a cooperative loop.
     * Cancellable via [cancelLoadAllPages] or by switching the active feed.
     */
    @Suppress("TooGenericExceptionCaught")
    fun loadAllPages() {
        val feedId = mutableState.value.activeFeedId ?: return
        if (!mutableState.value.isHubFeed) return
        if (loadAllPagesJob?.isActive == true || isLoadingNextPage) return
        AppLog.i(TAG, "loadAllPages start feedId=$feedId")
        loadAllPagesJob = viewModelScope.launch(Dispatchers.Default) {
            val progress = repository.loadAllPagesProgress(feedId)
            updateState {
                it.copy(
                    loadAllPages = LoadAllPagesUiState.Running(
                        pagesProcessed = progress.pagesProcessed,
                        totalPages = progress.totalPages,
                    ),
                    canLoadMore = true,
                    errorMessage = null,
                )
            }
            try {
                val loaded = repository.loadAllRemainingPages(feedId) { processed, total ->
                    updateState {
                        it.copy(loadAllPages = LoadAllPagesUiState.Running(processed, total))
                    }
                }
                AppLog.i(TAG, "loadAllPages completed feedId=$feedId loaded=$loaded")
            } catch (error: CancellationException) {
                AppLog.i(TAG, "loadAllPages cancelled feedId=$feedId")
                throw error
            } catch (error: Exception) {
                AppLog.w(TAG, "loadAllPages failed feedId=$feedId", error)
                // The repository keeps the paging cursor intact on failure, so the button stays
                // available and the user can retry from the page where the import stopped.
                updateState { it.copy(errorMessage = error.toLoadAllPagesMessage()) }
            } finally {
                val remaining = repository.hasMorePages(feedId)
                updateState {
                    it.copy(
                        loadAllPages = LoadAllPagesUiState.Idle,
                        canLoadMore = remaining,
                    )
                }
            }
        }
    }

    fun cancelLoadAllPages() {
        AppLog.i(TAG, "cancelLoadAllPages active=${loadAllPagesJob?.isActive}")
        loadAllPagesJob?.cancel()
        loadAllPagesJob = null
        updateState { it.copy(loadAllPages = LoadAllPagesUiState.Idle) }
    }

    private fun cancelLoadAllPagesOnFeedSwitch() {
        if (loadAllPagesJob?.isActive == true) {
            AppLog.i(TAG, "cancelLoadAllPagesOnFeedSwitch cancelling active job")
            loadAllPagesJob?.cancel()
            loadAllPagesJob = null
        }
        updateState { it.copy(loadAllPages = LoadAllPagesUiState.Idle) }
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
        updateState { it.copy(isArticleOpen = false, selectedArticleId = null, article = null) }
    }

    fun selectHub(hubId: String?, title: String? = null) {
        val current = mutableState.value
        if (hubId == null || current.selectedHubId == hubId) {
            selectBaseArticlesFeed()
            return
        }

        val hubTitle = title ?: current.hubTitle(hubId)
        openHubFeed(slug = hubId, title = hubTitle)
    }

    fun selectTag(tagId: String?, title: String? = null) {
        val current = mutableState.value
        updateState {
            val selected = if (tagId == null || it.selectedTagId == tagId) null else tagId
            val changed = selected != it.selectedTagId
            it.copy(
                selectedTagId = selected,
                selectedTagTitle = selected?.let { id -> title ?: current.tagTitle(id) },
                selectedPublicationSection = HabrPublicationSection.Articles,
                selectedDestination = ReaderDestination.Feed,
                isArticleOpen = false,
            )
                .let { next -> if (changed) next.requestFeedScrollToTop() else next }
        }
        reobserveAllCachedIfActive()
        refreshIfCurrentFeedIsEmpty()
    }

    fun toggleFavoriteTag(tagId: String) {
        viewModelScope.launch(Dispatchers.Default) {
            val title =
                mutableState.value.items.flatMap { it.tags }.firstOrNull { it.id == tagId }?.title
            repository.toggleFavoriteTag(tagId = tagId, title = title)
        }
    }

    fun toggleFavoriteHub(hubId: String) {
        viewModelScope.launch(Dispatchers.Default) {
            val currentState = mutableState.value
            val title = currentState.items.flatMap { it.hubs }.firstOrNull { it.id == hubId }?.title
                ?: currentState.favoriteHubs.firstOrNull { it.first == hubId }?.second
            repository.toggleFavoriteHub(hubId = hubId, title = title)
            repository.getFeeds(forceRefresh = true)
                .also { feeds -> updateState { it.copy(feeds = feeds) } }
        }
    }

    fun selectPublicationSection(section: HabrPublicationSection) {
        updateState {
            val changed = it.selectedPublicationSection != section ||
                    it.selectedHubId != null ||
                    it.selectedTagId != null ||
                    it.searchQuery.isNotBlank()
            it.copy(
                selectedPublicationSection = section,
                selectedDestination = ReaderDestination.Feed,
                isArticleOpen = false,
                selectedHubId = null,
                selectedHubTitle = null,
                selectedTagId = null,
                selectedTagTitle = null,
                searchQuery = "",
            )
                .let { next -> if (changed) next.requestFeedScrollToTop() else next }
        }
        reobserveAllCachedIfActive()
    }

    fun updateSearchQuery(query: String) {
        updateState {
            val changed = it.searchQuery != query
            it.copy(
                searchQuery = query,
                selectedDestination = it.selectedDestination,
                isArticleOpen = false,
            )
                .let { next -> if (changed) next.requestFeedScrollToTop() else next }
        }
        reobserveAllCachedIfActive()
    }

    fun clearFilters() {
        updateState {
            val changed = it.activeFilterCount > 0
            it.copy(
                selectedHubId = null,
                selectedHubTitle = null,
                selectedTagId = null,
                selectedTagTitle = null,
                searchQuery = "",
                showUnreadOnly = false,
                isArticleOpen = false,
            )
                .let { next -> if (changed) next.requestFeedScrollToTop() else next }
        }
        reobserveAllCachedIfActive()
    }

    fun setShowUnreadOnly(showUnreadOnly: Boolean) {
        updateState {
            val changed = it.showUnreadOnly != showUnreadOnly
            it.copy(showUnreadOnly = showUnreadOnly, isArticleOpen = false)
                .let { next -> if (changed) next.requestFeedScrollToTop() else next }
        }
        reobserveAllCachedIfActive()
    }

    fun dismissError() {
        updateState { it.copy(errorMessage = null) }
    }

    fun setFeedCardMode(mode: FeedCardMode) {
        updateState {
            it.copy(
                feedCardMode = mode,
                settings = it.settings.copy(feedCardMode = mode.name)
            )
        }
        viewModelScope.launch(Dispatchers.Default) {
            preferencesRepository.setFeedCardMode(mode.name)
            preferencesRepository.setCompactCards(mode == FeedCardMode.CompactText)
        }
    }

    fun setFeedSortMode(mode: FeedSortMode) {
        updateState {
            val changed = it.feedSortMode != mode
            it.copy(feedSortMode = mode)
                .let { next -> if (changed) next.requestFeedScrollToTop() else next }
        }
    }

    fun updateSettings(transform: (FeedSettings) -> FeedSettings) {
        viewModelScope.launch(Dispatchers.Default) {
            val current = mutableState.value.settings
            val next = transform(current)
            updateState { it.copy(settings = next) }
            if (next.fontScale != current.fontScale) preferencesRepository.setFontScale(next.fontScale)
            if (next.lineHeightScale != current.lineHeightScale) preferencesRepository.setLineHeightScale(
                next.lineHeightScale
            )
            if (next.themeMode != current.themeMode) preferencesRepository.setThemeMode(next.themeMode)
            if (next.compactCards != current.compactCards) preferencesRepository.setCompactCards(
                next.compactCards
            )
            if (next.openLinksInsideApp != current.openLinksInsideApp) {
                preferencesRepository.setOpenLinksInsideApp(next.openLinksInsideApp)
            }
        }
    }

    fun openArticleUrl(url: String) {
        viewModelScope.launch(Dispatchers.Default) {
            runLoading {
                val article = repository.getArticleByUrl(url)
                updateState {
                    it.copy(
                        selectedArticleId = article.id,
                        selectedArticleBookmarked = repository.isBookmarked(article.id),
                        isArticleOpen = true,
                        selectedDestination = ReaderDestination.Feed,
                        errorMessage = null,
                    )
                }
            }
        }
    }

    fun saveCustomFeed(id: String?, title: String, url: String) {
        if (url.isBlank()) return
        viewModelScope.launch(Dispatchers.Default) {
            runLoading {
                val wasActive = mutableState.value.activeFeedId == id
                repository.upsertCustomFeed(id, title, url)
                val feeds = repository.getFeeds(forceRefresh = true)
                val savedFeed = feeds.findHubFeed(url.toHubSlug())
                if (wasActive && savedFeed != null) {
                    observeFeed(savedFeed.id)
                    resetPager(savedFeed.id)
                }
                updateState {
                    it.copy(
                        feeds = feeds,
                        activeFeedId = if (wasActive && savedFeed != null) savedFeed.id else it.activeFeedId,
                        selectedHubId = if (wasActive && savedFeed != null) savedFeed.url.toHubSlug() else it.selectedHubId,
                        selectedHubTitle = if (wasActive && savedFeed != null) savedFeed.title else it.selectedHubTitle,
                        selectedTagId = if (wasActive && savedFeed != null) null else it.selectedTagId,
                        selectedTagTitle = if (wasActive && savedFeed != null) null else it.selectedTagTitle,
                        errorMessage = null,
                    )
                }
                if (wasActive && savedFeed != null) {
                    repository.refreshFeed(savedFeed.id)
                    updateState {
                        it.copy(
                            canLoadMore = repository.hasMorePages(savedFeed.id),
                            errorMessage = null
                        )
                    }
                }
            }
        }
    }

    fun removeCustomFeed(id: String) {
        viewModelScope.launch(Dispatchers.Default) {
            runLoading {
                repository.removeCustomFeed(id)
                val feeds = repository.getFeeds(forceRefresh = true)
                val activeFeedRemoved = mutableState.value.activeFeedId == id
                val nextFeedId =
                    if (activeFeedRemoved) feeds.firstOrNull { it.kind == FeedKind.All }?.id
                        ?: feeds.firstOrNull()?.id else null
                if (nextFeedId != null) {
                    cancelLoadAllPagesOnFeedSwitch()
                    observeFeed(nextFeedId)
                    resetPager(nextFeedId)
                }
                updateState {
                    it.copy(
                        feeds = feeds,
                        activeFeedId = nextFeedId ?: it.activeFeedId,
                        selectedHubId = if (activeFeedRemoved) null else it.selectedHubId,
                        selectedHubTitle = if (activeFeedRemoved) null else it.selectedHubTitle,
                        selectedTagId = if (activeFeedRemoved) null else it.selectedTagId,
                        selectedTagTitle = if (activeFeedRemoved) null else it.selectedTagTitle,
                    )
                }
                if (nextFeedId != null) refresh(scrollToTopOnNewItems = false)
            }
        }
    }

    fun openHubFeed(slug: String, title: String) {
        val normalizedSlug = slug.toHubSlug().takeIf { it.isNotBlank() } ?: return
        viewModelScope.launch(Dispatchers.Default) {
            runLoading {
                repository.upsertCustomFeed(id = null, title = title, url = normalizedSlug)
                val feeds = repository.getFeeds(forceRefresh = true)
                val feed = feeds.findHubFeed(normalizedSlug) ?: return@runLoading
                cancelLoadAllPagesOnFeedSwitch()
                observeFeed(feed.id)
                resetPager(feed.id)
                updateState {
                    it.copy(
                        feeds = feeds,
                        activeFeedId = feed.id,
                        selectedArticleId = null,
                        selectedArticleBookmarked = false,
                        article = null,
                        isArticleOpen = false,
                        selectedHubId = normalizedSlug,
                        selectedHubTitle = title.ifBlank { normalizedSlug },
                        selectedTagId = null,
                        selectedTagTitle = null,
                        searchQuery = "",
                        selectedPublicationSection = HabrPublicationSection.Articles,
                        selectedDestination = ReaderDestination.Feed,
                        canLoadMore = repository.hasMorePages(feed.id),
                        errorMessage = null,
                    )
                        .requestFeedScrollToTop()
                }
                // Force refresh so the hub bucket is repopulated with the hub's latest articles
                // from the Habr API. Without force, a fresh cache (1h TTL) early-returns and the
                // hub view keeps showing the stale/empty bucket while "Все загруженные" already has
                // the new articles loaded via other feeds.
                repository.refreshFeed(feed.id, force = true)
                updateState {
                    it.copy(
                        canLoadMore = repository.hasMorePages(feed.id),
                        errorMessage = null
                    )
                }
            }
        }
    }

    fun toggleArticleBookmark(articleId: String) {
        viewModelScope.launch(Dispatchers.Default) {
            repository.toggleBookmark(articleId)
            updateState { state ->
                state.copy(
                    selectedArticleBookmarked = if (state.selectedArticleId == articleId) {
                        repository.isBookmarked(articleId)
                    } else {
                        state.selectedArticleBookmarked
                    },
                )
            }
        }
    }

    private fun selectBaseArticlesFeed() {
        val baseFeedId = mutableState.value.feeds.firstOrNull { it.kind == FeedKind.All }?.id
            ?: mutableState.value.feeds.firstOrNull { it.kind != FeedKind.Hub && it.kind != FeedKind.Custom }?.id
            ?: return
        viewModelScope.launch(Dispatchers.Default) {
            cancelLoadAllPagesOnFeedSwitch()
            observeFeed(baseFeedId)
            resetPager(baseFeedId)
            updateState {
                it.copy(
                    activeFeedId = baseFeedId,
                    selectedHubId = null,
                    selectedHubTitle = null,
                    selectedTagId = null,
                    selectedTagTitle = null,
                    selectedArticleId = null,
                    selectedArticleBookmarked = false,
                    article = null,
                    isArticleOpen = false,
                    selectedPublicationSection = HabrPublicationSection.Articles,
                    selectedDestination = ReaderDestination.Feed,
                    searchQuery = "",
                    canLoadMore = if (baseFeedId == HabrApiSource.FeedIds.AllCached) {
                        localAllHasMore
                    } else {
                        repository.hasMorePages(baseFeedId)
                    },
                    errorMessage = null,
                )
                    .requestFeedScrollToTop()
            }
            refresh(scrollToTopOnNewItems = false)
        }
    }

    private fun refreshIfCurrentFeedIsEmpty() {
        if (mutableState.value.items.isEmpty()) {
            refresh()
        }
    }

    private suspend fun runLoading(block: suspend () -> Unit) {
        updateState { it.copy(isRefreshing = true, errorMessage = null) }
        try {
            block()
        } catch (e: CancellationException) {
            updateState { it.copy(isRefreshing = false) }
            throw e
        } catch (e: Exception) {
            updateState { it.copy(errorMessage = e.message ?: "Ошибка загрузки") }
        } finally {
            updateState { it.copy(isRefreshing = false) }
        }
    }

    private inline fun updateState(transform: (ReaderUiState) -> ReaderUiState) {
        val startedAt = Clock.System.now().toEpochMilliseconds()
        mutableState.update { current ->
            val next = transform(current)
            val key = next.derivedKey()
            val cached = derivedCache
            val usedCache = key == derivedCacheKey && cached != null
            val derived = if (usedCache) cached else computeDerived(next)
            derivedCacheKey = key
            derivedCache = derived
            next.copy(
                visibleItems = derived.visibleItems,
                hubFilters = derived.hubFilters,
                tagFilters = derived.tagFilters,
            ).also { updated ->
                AppLog.d(
                    TAG,
                    "updateState items=${updated.items.size} visible=${updated.visibleItems.size} " +
                            "hubs=${updated.hubFilters.size} tags=${updated.tagFilters.size} " +
                            "derivedCached=$usedCache destination=${updated.selectedDestination} " +
                            "elapsed=${Clock.System.now().toEpochMilliseconds() - startedAt}ms",
                )
            }
        }
    }

    /**
     * Derived data for the feed screen. Recomputed only when the inputs change; see [derivedKey].
     */
    private data class DerivedFeedData(
        val visibleItems: List<FeedItem>,
        val hubFilters: List<FeedFilterChipState>,
        val tagFilters: List<FeedFilterChipState>,
    )

    /**
     * Compact key of every input used by [computeVisibleItems] and [withFilterChips].
     *
     * Item lists are immutable snapshots produced by Room flows, so a content change always comes
     * with a new list reference; [System.identityHashCode] is O(1) and exact for our usage.
     * Favorites sets/maps are small, so their content hash is cheap and safe.
     */
    private fun ReaderUiState.derivedKey(): String = buildString {
        append(System.identityHashCode(items))
        append('|')
        append(System.identityHashCode(bookmarkedItems))
        append('|')
        append(System.identityHashCode(feeds))
        append('|')
        append(selectedDestination.name)
        append('|')
        append(selectedHubId.orEmpty())
        append('|')
        append(selectedTagId.orEmpty())
        append('|')
        append(searchQuery)
        append('|')
        append(showUnreadOnly)
        append('|')
        append(feedSortMode.name)
        append('|')
        append(activeFeedId.orEmpty())
        append('|')
        append(favoriteTagIds.hashCode())
        append('|')
        append(favoriteHubIds.hashCode())
        append('|')
        append(favoriteTagTitles.hashCode())
        append('|')
        append(favoriteHubTitles.hashCode())
    }

    private fun computeDerived(state: ReaderUiState): DerivedFeedData {
        val visibleItems = computeVisibleItems(state)
        val withChips = state.withFilterChips(visibleItems)
        return DerivedFeedData(
            visibleItems = visibleItems,
            hubFilters = withChips.hubFilters,
            tagFilters = withChips.tagFilters,
        )
    }

    private fun computeVisibleItems(state: ReaderUiState): List<FeedItem> {
        val sectionItems = when (state.selectedDestination) {
            ReaderDestination.Bookmarks -> state.bookmarkedItems
            ReaderDestination.Search -> state.items
            ReaderDestination.Feed,
            ReaderDestination.Sources,
            ReaderDestination.Settings -> state.items
        }
        val terms = state.searchQuery.split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        return sectionItems
            .asSequence()
            .filter { item -> !state.showUnreadOnly || !item.isRead }
            .filter { item ->
                // When the active feed is already a hub feed, getByFeed(hubFeedId) already scopes
                // the list to that hub's bucket, so the extra selectedHubId filter is redundant and
                // can silently drop items whose hub.id/slug doesn't string-match the opened slug.
                val hubScopedFeed = state.activeFeedId?.startsWith(HabrApiSource.FeedIds.HubPrefix) == true
                hubScopedFeed || state.selectedHubId == null || item.hubs.any { it.matchesHubFilter(state.selectedHubId) }
            }
            .filter { item -> state.selectedTagId == null || item.tags.any { it.id == state.selectedTagId } }
            .filter { item -> terms.all { term -> item.matchesSearchTerm(term) } }
            .distinctBy { it.articleIdentityKey() }
            .toList()
            .let { filtered ->
                if (state.activeFeedId == HabrApiSource.FeedIds.Daily) {
                    // The Daily pack is a curated snapshot: the server order (sourceOrder) must be
                    // kept instead of re-sorting by publication time or rating.
                    filtered
                } else {
                    when (state.feedSortMode) {
                        FeedSortMode.Newest -> filtered.sortedByDescending {
                            it.publishedAtEpoch ?: Long.MIN_VALUE
                        }

                        FeedSortMode.Rating -> filtered.sortedByDescending {
                            it.rating?.filter { char -> char.isDigit() || char == '-' }?.toIntOrNull()
                                ?: 0
                        }
                    }
                }
            }
    }

    private fun ReaderUiState.withFilterChips(visibleItems: List<FeedItem>): ReaderUiState {
        val activeFeed = feeds.firstOrNull { it.id == activeFeedId }
        val filterBaseItems =
            if (selectedDestination == ReaderDestination.Bookmarks) bookmarkedItems else items
        val filterHubCounts = filterBaseItems.hubCounts()
        val filterHubTitles = filterBaseItems.hubTitlesById()
        val allHubTitles = if (filterBaseItems === items) filterHubTitles else items.hubTitlesById()
        val filterTagCounts = filterBaseItems.tagCounts()
        val filterTagTitles = filterBaseItems.tagTitlesById()
        val allTagTitles = if (filterBaseItems === items) filterTagTitles else items.tagTitlesById()
        val customHubChips = feeds
            .filter { selectedDestination != ReaderDestination.Bookmarks && (it.kind == FeedKind.Hub || it.kind == FeedKind.Custom) }
            .map { feed ->
                FeedFilterChipState(
                    id = feed.id,
                    title = feed.title,
                    count = if (feed.id == activeFeedId) items.size else 0,
                    favorite = false,
                    selected = feed.id == activeFeedId && selectedHubId == null,
                    feedId = feed.id,
                )
            }
        val customHubSlugs = feeds
            .filter { it.kind == FeedKind.Hub || it.kind == FeedKind.Custom }
            .mapTo(mutableSetOf()) { it.url.toHubSlug() }
        val selectedHubChip = selectedHubId
            ?.takeUnless { id -> id in customHubSlugs }
            ?.let { id ->
            FeedFilterChipState(
                id = id,
                title = selectedHubTitle ?: hubTitle(id),
                count = visibleItems.size.takeIf { it > 0 } ?: filterHubCounts[id].orZero(),
                favorite = false,
                selected = true,
            )
        }
        val hubChips = (listOfNotNull(selectedHubChip) + customHubChips)
            .distinctBy { it.id }.sortedByDescending { it.selected }

        val tagSourceItems =
            if (selectedDestination == ReaderDestination.Bookmarks || selectedHubId != null || activeFeed?.kind == FeedKind.Hub || activeFeed?.kind == FeedKind.Custom) {
                visibleItems
            } else if (selectedDestination == ReaderDestination.Feed) {
                // "Новые" / "Все загруженные" (kind = All) ранее давали emptyList(), из-за чего
                // секция тегов скрывалась. Берём теги из всего пула загруженных статей фида,
                // чтобы фильтр по тегам был доступен для всех статей, а не только внутри хаба.
                items
            } else {
                emptyList()
            }
        val tagCounts = tagSourceItems.tagCounts()
        val selectedTagChip = selectedTagId?.let { id ->
            FeedFilterChipState(
                id = id,
                title = selectedTagTitle ?: tagTitle(id),
                count = visibleItems.size.takeIf { it > 0 } ?: filterTagCounts[id].orZero(),
                favorite = id in favoriteTagIds,
                selected = true,
            )
        }
        val favoriteTagChips = favoriteTagIds.mapNotNull { id ->
            val title = favoriteTagTitles[id] ?: filterTagTitles[id] ?: allTagTitles[id]
            ?: return@mapNotNull null
            if (title.looksLikeGeneratedId()) return@mapNotNull null
            FeedFilterChipState(
                id = id,
                title = title,
                count = tagCounts[id] ?: filterTagCounts[id].orZero(),
                favorite = true,
                selected = id == selectedTagId,
            )
        }
        val contextTagChips = tagSourceItems
            .asSequence()
            .flatMap { it.tags }
            .distinctBy { it.id }
            .filterNot { it.title.looksLikeGeneratedId() }
            .sortedByDescending { tagCounts[it.id] ?: 0 }
            .take(MAX_CONTEXT_FILTER_CHIPS)
            .map { tag ->
                FeedFilterChipState(
                    id = tag.id,
                    title = tag.title,
                    count = tagCounts[tag.id] ?: 0,
                    favorite = tag.id in favoriteTagIds,
                    selected = tag.id == selectedTagId,
                )
            }.toList()
        val tagChips = (listOfNotNull(selectedTagChip) + favoriteTagChips + contextTagChips)
            .distinctBy { it.id }

        return copy(hubFilters = hubChips, tagFilters = tagChips)
    }

    private fun FeedItem.articleIdentityKey(): String {
        val normalizedUrl = url
            .trim()
            .lowercase()
            .substringBefore("#")
            .substringBefore("?")
            .trimEnd('/')
        return normalizedUrl.ifBlank { title.trim().lowercase() }
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

private fun Hub.matchesHubFilter(filterId: String): Boolean {
    val normalizedFilter = filterId.toHubSlug()
    return id == filterId ||
        id.toHubSlug() == normalizedFilter ||
        slug?.toHubSlug() == normalizedFilter ||
        title.toHubSlug() == normalizedFilter
}

private fun FeedKind.toPublicationSection(): HabrPublicationSection = when (this) {
        FeedKind.All,
        FeedKind.Best,
        FeedKind.Hub,
        FeedKind.Tag,
        FeedKind.Search,
        FeedKind.Custom,
        FeedKind.Daily -> HabrPublicationSection.Articles

        FeedKind.Posts -> HabrPublicationSection.Posts
        FeedKind.News -> HabrPublicationSection.News
    }
}

private fun ReaderUiState.hubTitle(id: String): String =
    hubFilters.firstOrNull { it.id == id }?.title
        ?: favoriteHubTitles[id]
        ?: favoriteHubs.firstOrNull { it.first == id }?.second
        ?: visibleItems.hubTitle(id)
        ?: items.hubTitle(id)
        ?: id.removePrefix("hub-")

private fun ReaderUiState.tagTitle(id: String): String =
    tagFilters.firstOrNull { it.id == id }?.title
        ?: favoriteTagTitles[id]
        ?: favoriteTags.firstOrNull { it.first == id }?.second
        ?: visibleItems.tagTitle(id)
        ?: items.tagTitle(id)
        ?: id.removePrefix("tag-")

private fun List<FeedItem>.hubTitle(id: String): String? = asSequence()
    .flatMap { it.hubs.asSequence() }
    .firstOrNull { it.id == id }
    ?.title

private fun List<FeedItem>.tagTitle(id: String): String? = asSequence()
    .flatMap { it.tags.asSequence() }
    .firstOrNull { it.id == id }
    ?.title

private fun List<FeedItem>.tagCounts(): Map<String, Int> =
    flatMap { item -> item.tags.map { it.id } }
        .groupingBy { it }
        .eachCount()

private fun List<FeedItem>.hubCounts(): Map<String, Int> =
    flatMap { item -> item.hubs.map { it.id } }
        .groupingBy { it }
        .eachCount()

private fun List<FeedItem>.hubTitlesById(): Map<String, String> = buildMap {
    this@hubTitlesById.forEach { item ->
        item.hubs.forEach { hub -> putIfAbsent(hub.id, hub.title) }
    }
}

private fun List<FeedItem>.tagTitlesById(): Map<String, String> = buildMap {
    this@tagTitlesById.forEach { item ->
        item.tags.forEach { tag -> putIfAbsent(tag.id, tag.title) }
    }
}

private fun Int?.orZero(): Int = this ?: 0

private fun FeedIntent.logSummary(): String = when (this) {
    FeedIntent.Refresh -> "Refresh"
    FeedIntent.LoadMore -> "LoadMore"
    FeedIntent.LoadAllPages -> "LoadAllPages"
    FeedIntent.CancelLoadAllPages -> "CancelLoadAllPages"
    FeedIntent.DismissError -> "DismissError"
    is FeedIntent.SelectFeed -> "SelectFeed feedId=$feedId"
    is FeedIntent.SelectDestination -> "SelectDestination destination=$destination"
    is FeedIntent.SelectArticle -> "SelectArticle articleId=$articleId"
    FeedIntent.CloseArticle -> "CloseArticle"
    is FeedIntent.SelectHub -> "SelectHub hubId=$hubId title=$title"
    is FeedIntent.SelectTag -> "SelectTag tagId=$tagId title=$title"
    is FeedIntent.ToggleFavoriteTag -> "ToggleFavoriteTag tagId=$tagId"
    is FeedIntent.ToggleFavoriteHub -> "ToggleFavoriteHub hubId=$hubId"
    is FeedIntent.SelectPublicationSection -> "SelectPublicationSection section=$section"
    is FeedIntent.UpdateSearchQuery -> "UpdateSearchQuery length=${query.length}"
    FeedIntent.ClearFilters -> "ClearFilters"
    is FeedIntent.SetShowUnreadOnly -> "SetShowUnreadOnly value=$showUnreadOnly"
    is FeedIntent.SetFeedCardMode -> "SetFeedCardMode mode=$mode"
    is FeedIntent.SetFeedSortMode -> "SetFeedSortMode mode=$mode"
    is FeedIntent.UpdateSettings -> "UpdateSettings"
    is FeedIntent.SaveCustomFeed -> "SaveCustomFeed id=$id title=$title url=$url"
    is FeedIntent.RemoveCustomFeed -> "RemoveCustomFeed id=$id"
    is FeedIntent.OpenArticleUrl -> "OpenArticleUrl url=$url"
    is FeedIntent.OpenHubFeed -> "OpenHubFeed slug=$slug title=$title"
    is FeedIntent.ToggleArticleBookmark -> "ToggleArticleBookmark articleId=$articleId"
}

private fun String.looksLikeGeneratedId(): Boolean = trim().matches(Regex("-?\\d+"))

private fun Throwable.toLoadAllPagesMessage(): String = when (this) {
    is HabrRemoteException.RateLimited -> "Лимит запросов Хабра. Попробуйте позже."
    is HabrRemoteException.Server -> "Ошибка сервера Хабра (${status}). Попробуйте позже."
    is HabrRemoteException.ContractChanged -> "Сервис Хабра изменился. Обновите приложение."
    else -> message ?: "Ошибка загрузки"
}

private const val MAX_CONTEXT_FILTER_CHIPS = 16
// Must stay in sync with TechReaderRepository.LOCAL_ALL_PAGE_SIZE.
private const val LOCAL_ALL_PAGE_SIZE = 200
private const val TAG = "FeedViewModel"

private fun FeedSettings.toFeedCardMode(): FeedCardMode =
    FeedCardMode.entries.firstOrNull { it.name == feedCardMode }
        ?: if (compactCards) FeedCardMode.CompactText else FeedCardMode.Comfortable

private fun List<FeedDescriptor>.findHubFeed(slug: String): FeedDescriptor? {
    val normalizedSlug = slug.toHubSlug()
    return firstOrNull { feed ->
        (feed.kind == FeedKind.Hub || feed.kind == FeedKind.Custom) &&
                (feed.url.toHubSlug() == normalizedSlug || feed.id == HabrApiSource.FeedIds.hub(
                    normalizedSlug
                ))
    }
}

private fun String.toHubSlug(): String {
    val value = trim().replace("&amp;", "&").trimEnd('/')
    val slug = when {
        "/hubs/" in value -> value.substringAfterLast("/hubs/")
        "/hub/" in value -> value.substringAfterLast("/hub/")
        else -> value
    }
    return slug
        .substringBefore('/')
        .substringBefore('?')
        .trim()
        .replace(Regex("\\s+"), "_")
        .lowercase()
}
