package com.arny.habrrss.presentation

import com.arny.habrrss.domain.models.FeedSettings
import com.arny.habrrss.presentation.feed.HabrPublicationSection
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

class FeedViewModel(
    private val interactor: ReaderInteractor,
) : ViewModel() {
    val state: StateFlow<ReaderUiState> = interactor.state

    init {
        viewModelScope.launch {
            interactor.start()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            interactor.refresh()
        }
    }

    fun selectFeed(feedId: String) {
        viewModelScope.launch {
            interactor.selectFeed(feedId)
        }
    }

    fun loadArticleInPane(articleId: String) {
        viewModelScope.launch {
            interactor.selectArticle(articleId)
        }
    }

    fun loadMoreItems() {
        viewModelScope.launch {
            interactor.loadMoreItems()
        }
    }

    fun selectDestination(destination: ReaderDestination) {
        interactor.selectDestination(destination)
    }

    fun closeArticle() {
        interactor.closeArticle()
    }

    fun selectHub(hubId: String?) {
        interactor.selectHub(hubId)
    }

    fun selectTag(tagId: String?) {
        interactor.selectTag(tagId)
    }

    fun toggleFavoriteTag(tagId: String) {
        viewModelScope.launch {
            interactor.toggleFavoriteTag(tagId)
        }
    }

    fun toggleFavoriteHub(hubId: String) {
        viewModelScope.launch {
            interactor.toggleFavoriteHub(hubId)
        }
    }

    fun selectPublicationSection(section: HabrPublicationSection) {
        interactor.selectPublicationSection(section)
    }

    fun updateSearchQuery(query: String) {
        interactor.updateSearchQuery(query)
    }

    fun clearFilters() {
        interactor.clearFilters()
    }

    fun setShowUnreadOnly(showUnreadOnly: Boolean) {
        interactor.setShowUnreadOnly(showUnreadOnly)
    }

    fun setFeedCardMode(mode: FeedCardMode) {
        interactor.setFeedCardMode(mode)
    }

    fun setFeedSortMode(mode: FeedSortMode) {
        interactor.setFeedSortMode(mode)
    }

    fun updateSettings(transform: (FeedSettings) -> FeedSettings) {
        viewModelScope.launch {
            interactor.updateSettings(transform)
        }
    }

    fun openArticleUrl(url: String) {
        viewModelScope.launch {
            interactor.openArticleUrl(url)
        }
    }

    fun saveCustomFeed(id: String?, title: String, url: String) {
        viewModelScope.launch {
            interactor.saveCustomFeed(id, title, url)
        }
    }

    fun removeCustomFeed(id: String) {
        viewModelScope.launch {
            interactor.removeCustomFeed(id)
        }
    }

    fun toggleArticleBookmark(articleId: String) {
        viewModelScope.launch {
            interactor.toggleArticleBookmark(articleId)
        }
    }
}
