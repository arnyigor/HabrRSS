package com.arny.habrrss.ui.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arny.habrrss.presentation.ReaderUiState
import com.arny.habrrss.ui.feed.FeedList

@Composable
internal fun SearchScreen(
    state: ReaderUiState,
    onSearchChanged: (String) -> Unit,
    onArticleSelected: (String) -> Unit,
    onBookmark: (String) -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = onSearchChanged,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            placeholder = { Text("Заголовок, тег, автор") },
        )
        val computedVisibleItems = remember(
            state.items,
            state.showUnreadOnly,
            state.selectedHubId,
            state.selectedTagId,
            state.searchQuery,
            state.feedSortMode
        ) {
            state.visibleItems
        }
        FeedList(
            modifier = Modifier.fillMaxSize(),
            items = computedVisibleItems,
            selectedArticleId = state.selectedArticleId,
            cardMode = state.feedCardMode,
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            onArticleSelected = onArticleSelected,
            onBookmark = onBookmark,
            onClearFilters = { onSearchChanged("") },
        )
    }
}
