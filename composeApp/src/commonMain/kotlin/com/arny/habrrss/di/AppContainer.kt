package com.arny.habrrss.di

import com.arny.habrrss.core.network.createHttpClient
import com.arny.habrrss.data.api.HabrApiSource
import com.arny.habrrss.data.article.HabrArticleContentSource
import com.arny.habrrss.data.database.FeedDao
import com.arny.habrrss.data.database.createPlatformFeedDao
import com.arny.habrrss.data.repository.TechReaderRepository
import com.arny.habrrss.data.rss.GenericRssSource
import com.arny.habrrss.data.rss.HabrRssSource
import com.arny.habrrss.domain.usecases.GetFeedsUseCase
import com.arny.habrrss.domain.usecases.HasMorePagesUseCase
import com.arny.habrrss.domain.usecases.LoadNextPageUseCase
import com.arny.habrrss.domain.usecases.OpenArticleUseCase
import com.arny.habrrss.domain.usecases.RefreshFeedUseCase
import com.arny.habrrss.domain.usecases.ToggleBookmarkUseCase
import com.arny.habrrss.presentation.ReaderPresenter
import io.ktor.client.HttpClient

/**
 * DI Container for the application.
 *
 * Responsibilities:
 * - Manages singleton lifecycle of HTTP client
 * - Provides repository and presenter instances
 * - Handles proper cleanup on app termination
 *
 * Usage:
 * - Android: Create in Application.onCreate(), store in lateinit var or singleton
 * - Desktop: Create in main(), close on exit
 */
open class AppContainer(
    private val enableLogging: Boolean = false,
) {
    // HTTP client - singleton, must be closed
    val httpClient: HttpClient by lazy {
        createHttpClient(enableLogging = enableLogging)
    }

    // Platform FeedDao for persistent storage
    val feedDao: FeedDao by lazy {
        createPlatformFeedDao()
    }

    // Article source for full article loading
    private val articleContentSource: HabrArticleContentSource by lazy {
        HabrArticleContentSource(httpClient)
    }

    // Repository instance
    val repository: TechReaderRepository by lazy {
        TechReaderRepository(
            primarySource = HabrRssSource(httpClient),
            feedDao = feedDao,
            articleContentSource = articleContentSource,
            secondarySources = listOf(
                GenericRssSource(),
                HabrApiSource(),
            ),
        )
    }

    // Presenter factory
    fun createReaderPresenter(): ReaderPresenter {
        return ReaderPresenter(
            repository = repository,
            getFeeds = GetFeedsUseCase(repository),
            refreshFeed = RefreshFeedUseCase(repository),
            openArticle = OpenArticleUseCase(repository),
            toggleBookmark = ToggleBookmarkUseCase(repository),
            loadNextPage = LoadNextPageUseCase(repository),
            hasMorePages = HasMorePagesUseCase(repository),
        )
    }

    /**
     * Cleanup resources. Call on app termination.
     */
    open fun close() {
        httpClient.close()
    }
}

/**
 * Android-specific container that stores context.
 * Use in Application.onCreate():
 *
 * class TechReaderApplication : Application() {
 *     lateinit var container: AppContainer
 *     override fun onCreate() {
 *         super.onCreate()
 *         container = AppContainer(enableLogging = BuildConfig.DEBUG)
 *     }
 *     override fun onTerminate() {
 *         container.close()
 *         super.onTerminate()
 *     }
 * }
 */
class AndroidAppContainer(
    private val enableLogging: Boolean = false,
) : AppContainer(enableLogging) {

    private var isClosed = false

    override fun close() {
        if (!isClosed) {
            isClosed = true
            super.close()
        }
    }
}
