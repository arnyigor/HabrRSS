package com.arny.habrrss.presentation

import com.arny.habrrss.domain.models.ArticleContent
import com.arny.habrrss.domain.models.CachePolicy
import com.arny.habrrss.domain.models.FeedDescriptor
import com.arny.habrrss.domain.models.FeedItem
import com.arny.habrrss.domain.models.FeedSettings
import com.arny.habrrss.presentation.feed.HabrPublicationSection

data class ReaderUiState(
    val destinations: List<ReaderDestination> = ReaderDestination.entries,
    val selectedDestination: ReaderDestination = ReaderDestination.Feed,
    val feeds: List<FeedDescriptor> = emptyList(),
    val activeFeedId: String? = null,
    val items: List<FeedItem> = emptyList(),
    val visibleItems: List<FeedItem> = emptyList(),
    val selectedArticleId: String? = null,
    val selectedArticleBookmarked: Boolean = false,
    val article: ArticleContent? = null,
    val isArticleOpen: Boolean = false,
    val selectedHubId: String? = null,
    val selectedTagId: String? = null,
    val selectedPublicationSection: HabrPublicationSection = HabrPublicationSection.Articles,
    val favoriteHubIds: Set<String> = emptySet(),
    val favoriteTagIds: Set<String> = emptySet(),
    val favoriteHubTitles: Map<String, String> = emptyMap(),
    val favoriteTagTitles: Map<String, String> = emptyMap(),
    val searchQuery: String = "",
    val showUnreadOnly: Boolean = false,
    val feedCardMode: FeedCardMode = FeedCardMode.Comfortable,
    val feedSortMode: FeedSortMode = FeedSortMode.Newest,
    val feedLoading: Boolean = false,
    val articleLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val canLoadMore: Boolean = false,
    val errorMessage: String? = null,
    val settings: FeedSettings = FeedSettings.defaults(),
) {

    val activeFilterCount: Int
        get() = listOf(
            selectedHubId != null,
            selectedTagId != null,
            searchQuery.isNotBlank(),
            showUnreadOnly,
        ).count { it }

    val favoriteTags: List<Pair<String, String>>
        get() {
            val knownTags = (favoriteTagTitles.entries.map { TagTitle(it.key, it.value) } +
                items.flatMap { item -> item.tags.map { TagTitle(it.id, it.title) } } +
                article?.tags.orEmpty().map { TagTitle(it.id, it.title) })
                .distinctBy { it.id }
                .associate { it.id to it.title }
            return favoriteTagIds
                .mapNotNull { id -> knownTags[id]?.takeUnless { it.looksLikeGeneratedId() }?.let { id to it } }
                .sortedBy { it.second.lowercase() }
        }

    val favoriteHubs: List<Pair<String, String>>
        get() {
            val knownHubs = (favoriteHubTitles.entries.map { HubTitle(it.key, it.value) } +
                items.flatMap { item -> item.hubs.map { HubTitle(it.id, it.title) } } +
                article?.hubs.orEmpty().map { HubTitle(it.id, it.title) })
                .distinctBy { it.id }
                .associate { it.id to it.title }
            return favoriteHubIds
                .mapNotNull { id -> knownHubs[id]?.takeUnless { it.looksLikeGeneratedId() }?.let { id to it } }
                .sortedBy { it.second.lowercase() }
        }

    val availableHubs: List<String>
        get() = items.flatMap { item -> item.hubs.map { it.id to it.title } }
            .distinctBy { it.first }
            .map { it.second }

    val availableTags: List<String>
        get() = items.flatMap { item -> item.tags.map { it.id to it.title } }
            .distinctBy { it.first }
            .map { it.second }

    val availableAuthors: List<Pair<String, String>>
        get() = items.mapNotNull { item -> item.author?.let { it.id to it.displayName } }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }

    val selectedFeedTitle: String
        get() = feeds.firstOrNull { it.id == activeFeedId }?.title ?: "Лента"

    val feedListStateKey: String
        get() = listOf(
            selectedDestination.name,
            activeFeedId.orEmpty(),
            selectedPublicationSection.name,
            selectedHubId.orEmpty(),
            selectedTagId.orEmpty(),
            searchQuery,
            showUnreadOnly.toString(),
            feedSortMode.name,
        ).joinToString("|")

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

private data class HubTitle(val id: String, val title: String)

private data class TagTitle(val id: String, val title: String)

private fun String.looksLikeGeneratedId(): Boolean = trim().matches(Regex("-?\\d+"))

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
