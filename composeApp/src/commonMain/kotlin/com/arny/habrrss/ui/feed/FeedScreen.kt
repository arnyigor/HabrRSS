package com.arny.habrrss.ui.feed

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.arny.habrrss.domain.models.Author
import com.arny.habrrss.domain.models.FeedDescriptor
import com.arny.habrrss.domain.models.FeedItem
import com.arny.habrrss.domain.models.FeedKind
import com.arny.habrrss.domain.models.Hub
import com.arny.habrrss.domain.models.Tag
import com.arny.habrrss.presentation.FeedCardMode
import com.arny.habrrss.presentation.ReaderUiState

@Composable
internal fun FeedScreen(
    isWide: Boolean,
    state: ReaderUiState,
    onHubSelected: (String?) -> Unit,
    onFeedSelected: (String) -> Unit,
    onTagSelected: (String?) -> Unit,
    onClearFilters: () -> Unit,
    onArticleSelected: (String) -> Unit,
    onBookmark: (String) -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        FeedFilterBar(
            state = state,
            onHubSelected = onHubSelected,
            onFeedSelected = onFeedSelected,
            onTagSelected = onTagSelected,
            onClearFilters = onClearFilters,
        )
        FeedBody(
            modifier = Modifier.fillMaxSize(),
            state = state,
            selectedArticleId = state.selectedArticleId,
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            onArticleSelected = onArticleSelected,
            onBookmark = onBookmark,
            onLoadMore = onLoadMore,
        )
    }
}

@Preview(name = "FeedScreen - Content", showBackground = true)
@Composable
private fun FeedScreenPreview() {
    MaterialTheme {
        FeedScreen(
            isWide = false,
            state = previewFeedState(),
            onHubSelected = {},
            onFeedSelected = {},
            onTagSelected = {},
            onClearFilters = {},
            onArticleSelected = {},
            onBookmark = {},
            isRefreshing = false,
            onRefresh = {},
            onLoadMore = {},
        )
    }
}

private fun previewFeedState(): ReaderUiState {
    val items = listOf(
        FeedItem(
            id = "article-1",
            feedId = "habr-all",
            title = "Navigation 3 и KMP: простой рабочий стек",
            summary = "Коротко о том, как держать навигацию предсказуемой и не смешивать её с UI-состоянием.",
            url = "https://habr.com/ru/articles/1/",
            imageUrl = null,
            author = Author("author", "Habr Author", null),
            publishedAt = "Сегодня",
            publishedAtEpoch = 1L,
            tags = listOf(Tag("tag-kmp", "KMP"), Tag("tag-compose", "Compose")),
            hubs = listOf(Hub("hub-mobile", "Mobile development")),
            rating = "+12",
            commentsCount = 4,
            isRead = false,
            isBookmarked = true,
        ),
        FeedItem(
            id = "article-2",
            feedId = "habr-all",
            title = "Room как single source of truth",
            summary = "Серверные данные отдельно, локальное состояние отдельно — обновления больше не затирают избранное.",
            url = "https://habr.com/ru/articles/2/",
            imageUrl = null,
            author = Author("author-2", "Database Dev", null),
            publishedAt = "Вчера",
            publishedAtEpoch = 0L,
            tags = listOf(Tag("tag-room", "Room")),
            hubs = listOf(Hub("hub-android", "Android")),
            rating = "+7",
            commentsCount = 1,
            isRead = true,
            isBookmarked = false,
        ),
    )
    return ReaderUiState(
        feeds = listOf(
            FeedDescriptor(
                id = "habr-all",
                title = "Все публикации",
                sourceTitle = "Habr",
                url = "https://habr.com/rss",
                description = "Preview feed",
                kind = FeedKind.All,
            )
        ),
        activeFeedId = "habr-all",
        items = items,
        visibleItems = items,
        favoriteTagIds = setOf("tag-kmp"),
        favoriteHubIds = setOf("hub-android"),
        canLoadMore = true,
    )
}
