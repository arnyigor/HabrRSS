package com.arny.habrrss

import com.arny.habrrss.domain.models.Author
import com.arny.habrrss.domain.models.FeedItem
import com.arny.habrrss.domain.models.Hub
import com.arny.habrrss.domain.models.Tag
import com.arny.habrrss.presentation.FeedSortMode
import com.arny.habrrss.presentation.ReaderDestination
import com.arny.habrrss.presentation.ReaderUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReaderUiStateTest {
    @Test
    fun visibleItemsFiltersByBookmarksUnreadHubTagAndSearch() {
        val matchItem = item(
            id = "match",
            title = "Coroutines guide",
            hub = Hub("android", "Android"),
            tag = Tag("kotlin", "Kotlin"),
            isRead = false,
            isBookmarked = true,
        )
        val state = ReaderUiState(
            selectedDestination = ReaderDestination.Bookmarks,
            selectedHubId = "android",
            selectedTagId = "kotlin",
            searchQuery = "coroutines",
            showUnreadOnly = true,
            items = listOf(
                matchItem,
                item(
                    id = "read",
                    title = "Coroutines guide",
                    hub = Hub("android", "Android"),
                    tag = Tag("kotlin", "Kotlin"),
                    isRead = true,
                    isBookmarked = true,
                ),
                item(
                    id = "not-bookmarked",
                    title = "Coroutines guide",
                    hub = Hub("android", "Android"),
                    tag = Tag("kotlin", "Kotlin"),
                    isRead = false,
                    isBookmarked = false,
                ),
            ),
            visibleItems = listOf(matchItem),
        )

        assertEquals(listOf("match"), state.visibleItems.map { it.id })
        assertEquals(4, state.activeFilterCount)
    }

    @Test
    fun visibleItemsSortsByRatingDigitsDescending() {
        val low = item("low", rating = "+2")
        val high = item("high", rating = "+15")
        val none = item("none", rating = null)
        val state = ReaderUiState(
            feedSortMode = FeedSortMode.Rating,
            items = listOf(low, high, none),
            visibleItems = listOf(high, low, none),
        )

        assertEquals(listOf("high", "low", "none"), state.visibleItems.map { it.id })
    }

    @Test
    fun exposesFavoriteHubsTagsAuthorsAndUnreadCount() {
        val state = ReaderUiState(
            favoriteHubIds = setOf("android"),
            favoriteTagIds = setOf("kotlin"),
            items = listOf(
                item(
                    id = "one",
                    author = Author("author", "Author", null),
                    hub = Hub("android", "Android"),
                    tag = Tag("kotlin", "Kotlin"),
                    isRead = false,
                ),
                item(
                    id = "two",
                    author = Author("author", "Author", null),
                    hub = Hub("desktop", "Desktop"),
                    tag = Tag("compose", "Compose"),
                    isRead = true,
                ),
            ),
        )

        assertEquals(listOf("Android"), state.favoriteHubs.map { it.second })
        assertEquals(listOf("Kotlin"), state.favoriteTags.map { it.second })
        assertEquals(listOf("Author"), state.availableAuthors.map { it.second })
        assertEquals(1, state.unreadCount)
        assertTrue(state.availableHubs.containsAll(listOf("Android", "Desktop")))
        assertTrue(state.availableTags.containsAll(listOf("Kotlin", "Compose")))
    }

    private fun item(
        id: String,
        title: String = id,
        author: Author? = Author("author-$id", "Author $id", null),
        hub: Hub = Hub("hub-$id", "Hub $id"),
        tag: Tag = Tag("tag-$id", "Tag $id"),
        rating: String? = "+1",
        isRead: Boolean = false,
        isBookmarked: Boolean = false,
    ): FeedItem = FeedItem(
        id = id,
        feedId = "feed",
        title = title,
        summary = "summary",
        url = "https://example.com/$id",
        imageUrl = null,
        author = author,
        publishedAt = "2026-05-02",
        publishedAtEpoch = null,
        tags = listOf(tag),
        hubs = listOf(hub),
        rating = rating,
        commentsCount = 0,
        isRead = isRead,
        isBookmarked = isBookmarked,
    )
}
