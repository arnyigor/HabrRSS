package com.arny.habrrss.presentation

import com.arny.habrrss.domain.models.FeedSettings
import com.arny.habrrss.presentation.feed.HabrPublicationSection
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

class FeedViewModel(
    private val presenter: ReaderPresenter,
) : ViewModel() {
    val state: StateFlow<ReaderUiState> = presenter.state

    init {
        viewModelScope.launch {
            presenter.start()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            presenter.refresh()
        }
    }

    fun selectFeed(feedId: String) {
        viewModelScope.launch {
            presenter.selectFeed(feedId)
        }
    }

    fun loadArticleInPane(articleId: String) {
        viewModelScope.launch {
            presenter.selectArticle(articleId)
        }
    }

    fun loadMoreItems() {
        viewModelScope.launch {
            presenter.loadMoreItems()
        }
    }

    fun selectDestination(destination: ReaderDestination) {
        presenter.selectDestination(destination)
    }

    fun closeArticle() {
        presenter.closeArticle()
    }

    fun selectHub(hubId: String?) {
        presenter.selectHub(hubId)
    }

    fun selectTag(tagId: String?) {
        presenter.selectTag(tagId)
    }

    fun toggleFavoriteTag(tagId: String) {
        presenter.toggleFavoriteTag(tagId)
    }

    fun toggleFavoriteHub(hubId: String) {
        presenter.toggleFavoriteHub(hubId)
    }

    fun selectPublicationSection(section: HabrPublicationSection) {
        presenter.selectPublicationSection(section)
    }

    fun updateSearchQuery(query: String) {
        presenter.updateSearchQuery(query)
    }

    fun clearFilters() {
        presenter.clearFilters()
    }

    fun setShowUnreadOnly(showUnreadOnly: Boolean) {
        presenter.setShowUnreadOnly(showUnreadOnly)
    }

    fun setFeedCardMode(mode: FeedCardMode) {
        presenter.setFeedCardMode(mode)
    }

    fun setFeedSortMode(mode: FeedSortMode) {
        presenter.setFeedSortMode(mode)
    }

    fun updateSettings(transform: (FeedSettings) -> FeedSettings) {
        presenter.updateSettings(transform)
    }

    fun toggleArticleBookmark(articleId: String) {
        viewModelScope.launch {
            presenter.toggleArticleBookmark(articleId)
        }
    }
}
