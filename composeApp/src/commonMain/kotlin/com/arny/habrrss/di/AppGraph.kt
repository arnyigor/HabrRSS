package com.arny.habrrss.di

import com.arny.habrrss.core.network.createHttpClient
import com.arny.habrrss.data.api.HabrApiSource
import com.arny.habrrss.data.database.AppDatabase
import com.arny.habrrss.data.database.FeedDao
import com.arny.habrrss.data.database.InMemoryFeedDao
import com.arny.habrrss.data.database.RoomFeedDao
import com.arny.habrrss.data.database.getDatabaseBuilder
import com.arny.habrrss.data.repository.TechReaderRepository
import com.arny.habrrss.data.rss.GenericRssSource
import com.arny.habrrss.data.rss.HabrRssSource
import com.arny.habrrss.domain.usecases.GetFeedsUseCase
import com.arny.habrrss.domain.usecases.OpenArticleUseCase
import com.arny.habrrss.domain.usecases.RefreshFeedUseCase
import com.arny.habrrss.domain.usecases.ToggleBookmarkUseCase
import com.arny.habrrss.presentation.ReaderPresenter

object AppGraph {
    // Platform-specific FeedDao initialization
    private val feedDao: FeedDao by lazy {
        try {
            val db = getDatabaseBuilder().build()
            RoomFeedDao(db.feedDao())
        } catch (e: UnsupportedOperationException) {
            // Desktop fallback - Room not supported
            InMemoryFeedDao()
        } catch (e: Exception) {
            // Fallback to in-memory if Room fails (e.g., first run, migration issues)
            InMemoryFeedDao()
        }
    }
    
    fun createReaderPresenter(): ReaderPresenter {
        // Note: enableLogging=true for debug builds only. 
        // Set to false or use BuildConfig.DEBUG in production.
        val httpClient = createHttpClient(enableLogging = true)
        val repository = TechReaderRepository(
            primarySource = HabrRssSource(httpClient),
            feedDao = feedDao,
            secondarySources = listOf(
                GenericRssSource(),
                HabrApiSource(),
            ),
        )
        return ReaderPresenter(
            repository = repository,
            getFeeds = GetFeedsUseCase(repository),
            refreshFeed = RefreshFeedUseCase(repository),
            openArticle = OpenArticleUseCase(repository),
            toggleBookmark = ToggleBookmarkUseCase(repository),
        )
    }
}