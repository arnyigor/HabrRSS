package com.arny.habrrss.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cash.paging.Pager
import app.cash.paging.PagingConfig
import app.cash.paging.PagingData
import app.cash.paging.PagingSourceLoadParamsAppend
import app.cash.paging.PagingSourceLoadResultError
import app.cash.paging.PagingSourceLoadResultInvalid
import app.cash.paging.PagingSourceLoadResultPage
import app.cash.paging.cachedIn
import com.arny.habrrss.data.preferences.UserPreferencesRepository
import com.arny.habrrss.data.repository.TechReaderRepository
import com.arny.habrrss.domain.models.FeedDescriptor
import com.arny.habrrss.domain.models.FeedItem
import com.arny.habrrss.domain.models.FeedSettings
import com.arny.habrrss.domain.models.FeedKind
import com.arny.habrrss.presentation.feed.HabrPublicationSection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface FeedIntent {
    data object Refresh : FeedIntent
    data object LoadMore : FeedIntent
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
    private var localStateJob: Job? = null
    private var isLoadingNextPage = false
    private var pagingSource: FeedPagingSource? = null
    private var nextPagingKey: Int? = FIRST_APPEND_PAGE

    private val pagingConfig = PagingConfig(
        pageSize = PAGE_SIZE,
        prefetchDistance = PAGE_PREFETCH_DISTANCE,
        enablePlaceholders = false,
    )
    private var pagingFlow: Flow<PagingData<FeedItem>>? = null

    init {
        viewModelScope.launch { start() }
    }

    fun dispatch(intent: FeedIntent) {
        when (intent) {
            FeedIntent.Refresh -> refresh()
            FeedIntent.LoadMore -> loadMoreItems()
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
        val settings = preferencesRepository.preferences().first()
        migrateFavoriteMetadataFromPreferences()
        val feeds = repository.getFeeds()
        val activeFeedId = feeds.firstOrNull()?.id ?: repository.requireFirstFeedId()
        updateState {
            it.copy(
                settings = settings,
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
        observeLocalFavorites()
        resetPager(activeFeedId)
        refresh()
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
        feedJob?.cancel()
        feedJob = viewModelScope.launch {
            repository.observeFeed(feedId).collect { items ->
                updateState { state ->
                    val selected = state.selectedArticleId
                    state.copy(
                        items = items,
                        selectedArticleBookmarked = items.firstOrNull { it.id == selected }?.isBookmarked
                            ?: state.selectedArticleBookmarked,
                    )
                }
            }
        }
    }

    private fun observeLocalFavorites() {
        if (localStateJob != null) return
        localStateJob = viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                repository.observeFavoriteHubIds(),
                repository.observeFavoriteTagIds(),
                repository.observeFavoriteHubTitles(),
                repository.observeFavoriteTagTitles(),
            ) { hubIds, tagIds, hubTitles, tagTitles ->
                FavoriteMetadata(hubIds, tagIds, hubTitles, tagTitles)
            }
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
        pagingSource = FeedPagingSource(repository = repository, feedId = feedId)
        nextPagingKey = FIRST_APPEND_PAGE
        pagingFlow = Pager(config = pagingConfig) {
            FeedPagingSource(repository = repository, feedId = feedId)
        }.flow.cachedIn(viewModelScope)
    }

    fun refresh() {
        val feedId = mutableState.value.activeFeedId ?: return
        viewModelScope.launch {
            runLoading {
                repository.getFeeds(forceRefresh = true).also { feeds -> updateState { it.copy(feeds = feeds) } }
                repository.refreshFeed(feedId)
                updateState { it.copy(canLoadMore = repository.hasMorePages(feedId), errorMessage = null) }
            }
        }
    }

    fun selectFeed(feedId: String) {
        viewModelScope.launch {
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
                    canLoadMore = repository.hasMorePages(feedId),
                    errorMessage = null,
                )
            }
            refresh()
        }
    }

    fun loadArticleInPane(articleId: String) {
        selectArticle(articleId)
    }

    fun selectArticle(articleId: String) {
        updateState { state ->
            state.copy(
                selectedArticleId = articleId,
                selectedArticleBookmarked = state.items.firstOrNull { it.id == articleId }?.isBookmarked ?: false,
                isArticleOpen = true,
                selectedDestination = ReaderDestination.Feed,
            )
        }
    }

    fun loadMoreItems() {
        val feedId = mutableState.value.activeFeedId ?: return
        if (isLoadingNextPage || !repository.hasMorePages(feedId)) return
        viewModelScope.launch {
            isLoadingNextPage = true
            try {
                val source = pagingSource ?: FeedPagingSource(repository = repository, feedId = feedId).also {
                    pagingSource = it
                }
                val key = nextPagingKey ?: return@launch
                when (val result = source.load(PagingSourceLoadParamsAppend(key, PAGE_SIZE, false))) {
                    is PagingSourceLoadResultPage -> {
                        nextPagingKey = result.nextKey
                        updateState { it.copy(canLoadMore = result.nextKey != null, errorMessage = null) }
                    }
                    is PagingSourceLoadResultError -> throw result.throwable
                    is PagingSourceLoadResultInvalid -> updateState { it.copy(canLoadMore = false) }
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
        val baseFeedId = current.feeds.firstOrNull { it.kind != FeedKind.Custom }?.id
        val activeFeed = current.feeds.firstOrNull { it.id == current.activeFeedId }
        val switchedToBaseFeed = activeFeed?.kind == FeedKind.Custom && baseFeedId != null
        if (switchedToBaseFeed) {
            observeFeed(baseFeedId)
            resetPager(baseFeedId)
        }
        updateState {
            val selected = if (hubId == null || it.selectedHubId == hubId) null else hubId
            it.copy(
                activeFeedId = if (switchedToBaseFeed) baseFeedId else it.activeFeedId,
                selectedHubId = selected,
                selectedHubTitle = selected?.let { id -> title ?: current.hubTitle(id) },
                selectedTagId = null,
                selectedTagTitle = null,
                selectedPublicationSection = HabrPublicationSection.Articles,
                selectedDestination = ReaderDestination.Feed,
                isArticleOpen = false,
            )
        }
        if (switchedToBaseFeed) {
            refresh()
        } else {
            refreshIfCurrentFeedIsEmpty()
        }
    }

    fun selectTag(tagId: String?, title: String? = null) {
        val current = mutableState.value
        updateState {
            val selected = if (tagId == null || it.selectedTagId == tagId) null else tagId
            it.copy(
                selectedTagId = selected,
                selectedTagTitle = selected?.let { id -> title ?: current.tagTitle(id) },
                selectedPublicationSection = HabrPublicationSection.Articles,
                selectedDestination = ReaderDestination.Feed,
                isArticleOpen = false,
            )
        }
        refreshIfCurrentFeedIsEmpty()
    }

    fun toggleFavoriteTag(tagId: String) {
        viewModelScope.launch {
            val title = mutableState.value.items.flatMap { it.tags }.firstOrNull { it.id == tagId }?.title
            repository.toggleFavoriteTag(tagId = tagId, title = title)
        }
    }

    fun toggleFavoriteHub(hubId: String) {
        viewModelScope.launch {
            val currentState = mutableState.value
            val title = currentState.items.flatMap { it.hubs }.firstOrNull { it.id == hubId }?.title
                ?: currentState.favoriteHubs.firstOrNull { it.first == hubId }?.second
            repository.toggleFavoriteHub(hubId = hubId, title = title)
            repository.getFeeds(forceRefresh = true).also { feeds -> updateState { it.copy(feeds = feeds) } }
        }
    }

    fun selectPublicationSection(section: HabrPublicationSection) {
        updateState {
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
        }
    }

    fun updateSearchQuery(query: String) {
        updateState {
            it.copy(
                searchQuery = query,
                selectedDestination = it.selectedDestination,
                isArticleOpen = false,
            )
        }
    }

    fun clearFilters() {
        updateState {
            it.copy(
                selectedHubId = null,
                selectedHubTitle = null,
                selectedTagId = null,
                selectedTagTitle = null,
                searchQuery = "",
                showUnreadOnly = false,
                isArticleOpen = false,
            )
        }
    }

    fun setShowUnreadOnly(showUnreadOnly: Boolean) {
        updateState { it.copy(showUnreadOnly = showUnreadOnly, isArticleOpen = false) }
    }

    fun setFeedCardMode(mode: FeedCardMode) {
        updateState { it.copy(feedCardMode = mode) }
    }

    fun setFeedSortMode(mode: FeedSortMode) {
        updateState { it.copy(feedSortMode = mode) }
    }

    fun updateSettings(transform: (FeedSettings) -> FeedSettings) {
        viewModelScope.launch {
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
    }

    fun openArticleUrl(url: String) {
        viewModelScope.launch {
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
        viewModelScope.launch {
            repository.upsertCustomFeed(id, title, url)
            val feeds = repository.getFeeds(forceRefresh = true)
            updateState { it.copy(feeds = feeds) }
        }
    }

    fun removeCustomFeed(id: String) {
        viewModelScope.launch {
            repository.removeCustomFeed(id)
            val feeds = repository.getFeeds(forceRefresh = true)
            val activeFeedRemoved = mutableState.value.activeFeedId == id
            updateState {
                it.copy(
                    feeds = feeds,
                    activeFeedId = if (activeFeedRemoved) feeds.firstOrNull()?.id else it.activeFeedId,
                )
            }
        }
    }

    fun openHubFeed(slug: String, title: String) {
        val normalizedSlug = slug.toHubSlug().takeIf { it.isNotBlank() } ?: return
        viewModelScope.launch {
            runLoading {
                repository.upsertCustomFeed(id = null, title = title, url = normalizedSlug)
                val feeds = repository.getFeeds(forceRefresh = true)
                val feed = feeds.findHubFeed(normalizedSlug) ?: return@runLoading
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
                        selectedHubId = null,
                        selectedTagId = null,
                        searchQuery = "",
                        selectedPublicationSection = HabrPublicationSection.Articles,
                        selectedDestination = ReaderDestination.Feed,
                        canLoadMore = repository.hasMorePages(feed.id),
                        errorMessage = null,
                    )
                }
                repository.refreshFeed(feed.id)
                updateState { it.copy(canLoadMore = repository.hasMorePages(feed.id), errorMessage = null) }
            }
        }
    }

    fun toggleArticleBookmark(articleId: String) {
        viewModelScope.launch {
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
        mutableState.update { current ->
            val next = transform(current)
            val visibleItems = computeVisibleItems(next)
            next.copy(visibleItems = visibleItems)
                .withFilterChips(visibleItems)
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
            .distinctBy { it.articleIdentityKey() }
            .let { filtered ->
                when (state.feedSortMode) {
                    FeedSortMode.Newest -> filtered.sortedByDescending { it.publishedAtEpoch ?: Long.MIN_VALUE }
                    FeedSortMode.Rating -> filtered.sortedByDescending {
                        it.rating?.filter { char -> char.isDigit() || char == '-' }?.toIntOrNull() ?: 0
                    }
                }
            }
    }

    private fun ReaderUiState.withFilterChips(visibleItems: List<FeedItem>): ReaderUiState {
        val activeFeed = feeds.firstOrNull { it.id == activeFeedId }
        val customHubChips = feeds
            .filter { it.kind == FeedKind.Custom }
            .map { feed ->
                FeedFilterChipState(
                    id = feed.id,
                    title = feed.title,
                    count = if (feed.id == activeFeedId) items.size else 0,
                    favorite = true,
                    selected = feed.id == activeFeedId && selectedHubId == null,
                    feedId = feed.id,
                )
            }
        val selectedHubChip = selectedHubId?.let { id ->
            FeedFilterChipState(
                id = id,
                title = selectedHubTitle ?: hubTitle(id),
                count = visibleItems.size.takeIf { it > 0 } ?: items.count { item -> item.hubs.any { it.id == id } },
                favorite = id in favoriteHubIds,
                selected = true,
            )
        }
        val favoriteHubChips = favoriteHubIds.mapNotNull { id ->
            val title = favoriteHubTitles[id] ?: items.hubTitle(id) ?: return@mapNotNull null
            if (title.looksLikeGeneratedId()) return@mapNotNull null
            FeedFilterChipState(
                id = id,
                title = title,
                count = items.count { item -> item.hubs.any { it.id == id } },
                favorite = true,
                selected = id == selectedHubId,
            )
        }
        val hubChips = (listOfNotNull(selectedHubChip) + customHubChips + favoriteHubChips)
            .distinctBy { it.id }

        val tagSourceItems = if (selectedHubId != null || activeFeed?.kind == FeedKind.Custom) visibleItems else emptyList()
        val tagCounts = tagSourceItems.tagCounts()
        val selectedTagChip = selectedTagId?.let { id ->
            FeedFilterChipState(
                id = id,
                title = selectedTagTitle ?: tagTitle(id),
                count = visibleItems.size.takeIf { it > 0 } ?: items.count { item -> item.tags.any { it.id == id } },
                favorite = id in favoriteTagIds,
                selected = true,
            )
        }
        val favoriteTagChips = favoriteTagIds.mapNotNull { id ->
            val title = favoriteTagTitles[id] ?: items.tagTitle(id) ?: return@mapNotNull null
            if (title.looksLikeGeneratedId()) return@mapNotNull null
            FeedFilterChipState(
                id = id,
                title = title,
                count = tagCounts[id] ?: items.count { item -> item.tags.any { it.id == id } },
                favorite = true,
                selected = id == selectedTagId,
            )
        }
        val contextTagChips = tagSourceItems.flatMap { it.tags }
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
            }
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

    companion object {
        private const val PAGE_SIZE = 20
        private const val PAGE_PREFETCH_DISTANCE = 5
        private const val FIRST_APPEND_PAGE = 1
    }
}

private fun ReaderUiState.hubTitle(id: String): String = hubFilters.firstOrNull { it.id == id }?.title
    ?: favoriteHubTitles[id]
    ?: favoriteHubs.firstOrNull { it.first == id }?.second
    ?: visibleItems.hubTitle(id)
    ?: items.hubTitle(id)
    ?: id.removePrefix("hub-")

private fun ReaderUiState.tagTitle(id: String): String = tagFilters.firstOrNull { it.id == id }?.title
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

private fun List<FeedItem>.tagCounts(): Map<String, Int> = flatMap { item -> item.tags.map { it.id } }
    .groupingBy { it }
    .eachCount()

private fun String.looksLikeGeneratedId(): Boolean = trim().matches(Regex("-?\\d+"))

private const val MAX_CONTEXT_FILTER_CHIPS = 16

private fun List<FeedDescriptor>.findHubFeed(slug: String): FeedDescriptor? = firstOrNull { feed ->
    feed.kind == FeedKind.Custom && feed.url.toHubSlug() == slug
}

private fun String.toHubSlug(): String = trim()
    .replace("&amp;", "&")
    .trimEnd('/')
    .substringAfterLast("/hub/", this)
    .substringBefore('/')
    .substringBefore('?')
    .trim()
    .replace(Regex("\\s+"), "_")
    .lowercase()
