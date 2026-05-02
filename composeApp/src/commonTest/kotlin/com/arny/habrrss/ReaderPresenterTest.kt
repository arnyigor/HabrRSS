package com.arny.habrrss

import com.arny.habrrss.data.database.InMemoryFeedDao
import com.arny.habrrss.data.repository.TechReaderRepository
import com.arny.habrrss.domain.usecases.GetFeedsUseCase
import com.arny.habrrss.domain.usecases.OpenArticleUseCase
import com.arny.habrrss.domain.usecases.RefreshFeedUseCase
import com.arny.habrrss.domain.usecases.ToggleBookmarkUseCase
import com.arny.habrrss.presentation.FeedCardMode
import com.arny.habrrss.presentation.ReaderDestination
import com.arny.habrrss.presentation.ReaderPresenter
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderPresenterTest {
    @Test
    fun startLoadsFeedWithoutOpeningArticleOnMobile() = runTest {
        val presenter = createPresenter()
        
        // First verify that FakeFeedSource works
        val testFeedSource = FakeFeedSource()
        val feeds = testFeedSource.getFeeds()
        println("DEBUG: feeds from FakeFeedSource: ${feeds.size}, ${feeds.map { it.id }}")
        
        val items = testFeedSource.getItems("feed", null)
        println("DEBUG: items from FakeFeedSource: ${items.items.size}")

        presenter.start()
        // Wait for async loading
        kotlinx.coroutines.delay(200)

        val state = presenter.state.value
        println("DEBUG: items=${state.items.size}, feeds=${state.feeds.size}, activeFeedId=${state.activeFeedId}, error=${state.errorMessage}")
        assertEquals(2, state.items.size)
        assertEquals("feed", state.activeFeedId)
        assertFalse(state.isArticleOpen)
        assertEquals(null, state.article)
    }

    @Test
    fun selectArticleOpensReaderAndMarksItemAsRead() = runTest {
        val presenter = createPresenter()

        presenter.start()
        kotlinx.coroutines.delay(100)
        presenter.selectArticle("kotlin")
        kotlinx.coroutines.delay(100)

        val state = presenter.state.value
        assertTrue(state.isArticleOpen)
        assertEquals("kotlin", state.article?.id)
        assertTrue(state.items.first { it.id == "kotlin" }.isRead)
    }

    @Test
    fun filtersByHubTagAndSearchQuery() = runTest {
        val presenter = createPresenter()
        presenter.start()
        kotlinx.coroutines.delay(100)

        presenter.selectHub("android")
        assertEquals(listOf("kotlin"), presenter.state.value.visibleItems.map { it.id })

        presenter.selectTag("compose")
        assertEquals(listOf("compose"), presenter.state.value.visibleItems.map { it.id })

        presenter.updateSearchQuery("kotlin")
        assertEquals(ReaderDestination.Search, presenter.state.value.selectedDestination)
        assertEquals(listOf("kotlin"), presenter.state.value.visibleItems.map { it.id })
    }

    @Test
    fun articleTagSelectionClosesArticleAndFiltersFeed() = runTest {
        val presenter = createPresenter()
        presenter.start()
        kotlinx.coroutines.delay(100)
        presenter.selectArticle("kotlin")
        kotlinx.coroutines.delay(100)

        presenter.selectTag("compose")

        val state = presenter.state.value
        assertFalse(state.isArticleOpen)
        assertEquals("compose", state.selectedTagId)
        assertEquals(listOf("compose"), state.visibleItems.map { it.id })
    }

    @Test
    fun favoriteTagsCanBeToggled() = runTest {
        val presenter = createPresenter()
        presenter.start()
        kotlinx.coroutines.delay(100)

        presenter.toggleFavoriteTag("compose")

        assertEquals(setOf("compose"), presenter.state.value.favoriteTagIds)
        assertEquals(listOf("Compose"), presenter.state.value.favoriteTags.map { it.second })

        presenter.toggleFavoriteTag("compose")

        assertTrue(presenter.state.value.favoriteTagIds.isEmpty())
    }

    @Test
    fun unreadFilterCardModeAndReadingSettingsUpdateState() = runTest {
        val presenter = createPresenter()
        presenter.start()
        kotlinx.coroutines.delay(100)
        presenter.selectArticle("kotlin")
        kotlinx.coroutines.delay(100)

        presenter.closeArticle()
        presenter.setShowUnreadOnly(true)
        presenter.setFeedCardMode(FeedCardMode.CompactText)
        presenter.updateSettings { it.copy(fontScale = 1.2f, lineHeightScale = 1.45f) }

        val state = presenter.state.value
        assertFalse(state.isArticleOpen)
        assertEquals(listOf("compose"), state.visibleItems.map { it.id })
        assertEquals(FeedCardMode.CompactText, state.feedCardMode)
        assertEquals(1.2f, state.settings.fontScale)
        assertEquals(1.45f, state.settings.lineHeightScale)
    }

    private fun createPresenter(): ReaderPresenter {
        val repository = TechReaderRepository(FakeFeedSource(), InMemoryFeedDao())
        return ReaderPresenter(
            repository = repository,
            getFeeds = GetFeedsUseCase(repository),
            refreshFeed = RefreshFeedUseCase(repository),
            openArticle = OpenArticleUseCase(repository),
            toggleBookmark = ToggleBookmarkUseCase(repository),
        )
    }
}
