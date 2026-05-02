package com.arny.habrrss.presentation

import com.arny.habrrss.domain.models.ArticleContent
import com.arny.habrrss.domain.models.CachePolicy
import com.arny.habrrss.domain.models.FeedDescriptor
import com.arny.habrrss.domain.models.FeedItem
import com.arny.habrrss.domain.models.FeedSettings

data class ReaderUiState(
    val destinations: List<ReaderDestination> = ReaderDestination.entries,
    val selectedDestination: ReaderDestination = ReaderDestination.Feed,
    val feeds: List<FeedDescriptor> = emptyList(),
    val activeFeedId: String? = null,
    val items: List<FeedItem> = emptyList(),
    val selectedArticleId: String? = null,
    val article: ArticleContent? = null,
    val isArticleOpen: Boolean = false,
    val selectedHubId: String? = null,
    val selectedTagId: String? = null,
    val favoriteTagIds: Set<String> = emptySet(),
    val searchQuery: String = "",
    val showUnreadOnly: Boolean = false,
    val feedCardMode: FeedCardMode = FeedCardMode.Comfortable,
    val feedSortMode: FeedSortMode = FeedSortMode.Newest,
    val feedLoading: Boolean = false,
    val articleLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val settings: FeedSettings = FeedSettings.defaults(),
) {
    val visibleItems: List<FeedItem>
        get() {
            val sectionItems = when (selectedDestination) {
            ReaderDestination.Bookmarks -> items.filter { it.isBookmarked }
            ReaderDestination.Search -> items
            ReaderDestination.Feed,
            ReaderDestination.Sources,
            ReaderDestination.Settings -> items
            }
            return sectionItems
                .filter { item -> !showUnreadOnly || !item.isRead }
                .filter { item -> selectedHubId == null || item.hubs.any { it.id == selectedHubId } }
                .filter { item -> selectedTagId == null || item.tags.any { it.id == selectedTagId } }
                .filter { item ->
                    searchQuery.isBlank() ||
                        item.title.contains(searchQuery, ignoreCase = true) ||
                        item.summary.contains(searchQuery, ignoreCase = true) ||
                        item.tags.any { it.title.contains(searchQuery, ignoreCase = true) } ||
                        item.hubs.any { it.title.contains(searchQuery, ignoreCase = true) } ||
                        item.author?.displayName?.contains(searchQuery, ignoreCase = true) == true
                }
                .let { filtered ->
                    when (feedSortMode) {
                        FeedSortMode.Newest -> filtered.sortedByDescending { it.publishedAt.orEmpty() }
                        FeedSortMode.Rating -> filtered.sortedByDescending {
                            it.rating?.filter(Char::isDigit)?.toIntOrNull() ?: 0
                        }
                    }
                }
        }

    val activeFilterCount: Int
        get() = listOf(
            selectedHubId != null,
            selectedTagId != null,
            searchQuery.isNotBlank(),
            showUnreadOnly,
        ).count { it }

    val favoriteTags: List<Pair<String, String>>
        get() = items.flatMap { item -> item.tags.map { it.id to it.title } }
            .distinctBy { it.first }
            .filter { favoriteTagIds.contains(it.first) }
            .sortedBy { it.second.lowercase() }

    val availableHubs: List<String>
        get() = items.flatMap { item -> item.hubs.map { it.id to it.title } }
            .distinctBy { it.first }
            .map { it.second }

    val availableTags: List<String>
        get() = items.flatMap { item -> item.tags.map { it.id to it.title } }
            .distinctBy { it.first }
            .map { it.second }

    val selectedFeedTitle: String
        get() = feeds.firstOrNull { it.id == activeFeedId }?.title ?: "Лента"

    val unreadCount: Int
        get() = items.count { !it.isRead }

    val cachePolicyLabel: String
        get() = when (settings.offlinePolicy) {
            CachePolicy.CacheFirst -> "cache-first"
            CachePolicy.OfflineOnly -> "offline-only"
            CachePolicy.OnlineFirst -> "online-first"
            CachePolicy.RefreshInBackground -> "refresh-in-background"
        }
}

enum class ReaderDestination(val label: String) {
    Feed("Лента"),
    Sources("RSS"),
    Bookmarks("Сохр."),
    Search("Поиск"),
    Settings("Ещё"),
}

enum class FeedCardMode {
    CompactText,
    Comfortable,
    Magazine,
}

enum class FeedSortMode {
    Newest,
    Rating,
}
