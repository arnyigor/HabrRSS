package com.arny.habrrss.ui.feed

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.arny.habrrss.domain.models.Author
import com.arny.habrrss.domain.models.FeedDescriptor
import com.arny.habrrss.domain.models.FeedItem
import com.arny.habrrss.domain.models.FeedKind
import com.arny.habrrss.domain.models.Hub
import com.arny.habrrss.domain.models.Tag
import com.arny.habrrss.presentation.LoadAllPagesUiState
import com.arny.habrrss.presentation.ReaderUiState

@Composable
internal fun FeedScreen(
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
    onLoadAllPages: () -> Unit = {},
    onCancelLoadAllPages: () -> Unit = {},
) {
    Column(Modifier.fillMaxSize()) {
        FeedFilterBar(
            state = state,
            onHubSelected = onHubSelected,
            onFeedSelected = onFeedSelected,
            onTagSelected = onTagSelected,
            onClearFilters = onClearFilters,
            onLoadAllPages = onLoadAllPages,
            onCancelLoadAllPages = onCancelLoadAllPages
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

@Preview(name = "FeedScreen - Hub load all pages", showBackground = true)
@Composable
private fun FeedScreenHubLoadAllPreview() {
    MaterialTheme {
        FeedScreen(
            state = previewHubFeedState(),
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

private fun previewHubFeedState(): ReaderUiState =
    previewFeedState().copy(
        feeds = listOf(
            FeedDescriptor(
                id = "habr-hub:android:alltime",
                title = "Android",
                sourceTitle = "Habr API",
                url = "https://habr.com/ru/hubs/android/",
                description = "Preview hub feed",
                kind = FeedKind.Hub,
            )
        ),
        activeFeedId = "habr-hub:android:alltime",
        canLoadMore = true,
        loadAllPages = LoadAllPagesUiState.Running(pagesProcessed = 3, totalPages = 12),
    )


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
