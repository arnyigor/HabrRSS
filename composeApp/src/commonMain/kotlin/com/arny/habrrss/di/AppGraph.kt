package com.arny.habrrss.di

import com.arny.habrrss.core.network.createHttpClient
import com.arny.habrrss.data.api.HabrApiSource
import com.arny.habrrss.data.article.HabrArticleContentSource
import com.arny.habrrss.data.database.createPlatformFeedDao
import com.arny.habrrss.data.preferences.createUserPreferencesRepository
import com.arny.habrrss.data.repository.TechReaderRepository
import com.arny.habrrss.data.rss.GenericRssSource
import com.arny.habrrss.data.rss.HabrRssSource
import com.arny.habrrss.domain.usecases.GetFeedsUseCase
import com.arny.habrrss.domain.usecases.HasMorePagesUseCase
import com.arny.habrrss.domain.usecases.LoadNextPageUseCase
import com.arny.habrrss.domain.usecases.OpenArticleUseCase
import com.arny.habrrss.domain.usecases.RefreshFeedUseCase
import com.arny.habrrss.domain.usecases.ToggleBookmarkUseCase
import com.arny.habrrss.presentation.ReaderInteractor
import io.ktor.client.HttpClient

/**
 * AppGraph - simplified local dev mode.
 *
 * Uses RoomFeedDao for persistent storage on both Android and Desktop.
 * InMemoryFeedDao and FileBackedFeedDao are kept as fallbacks only.
 * For production use AppContainer which always uses Room.
 */
object AppGraph {
    // HttpClient with logging disabled for local dev
    private val httpClient: HttpClient by lazy {
        createHttpClient(enableLogging = false)
    }

    private val feedDao by lazy { createPlatformFeedDao() }

    // Article content source
    private val articleContentSource: HabrArticleContentSource by lazy {
        HabrArticleContentSource(httpClient)
    }

    private val preferencesRepository by lazy { createUserPreferencesRepository() }

    private val customRssSource by lazy { GenericRssSource(httpClient) }

    fun createReaderPresenter(): ReaderInteractor {
        val repository = TechReaderRepository(
            primarySource = HabrRssSource(httpClient),
            feedDao = feedDao,
            articleContentSource = articleContentSource,
            secondarySources = listOf(
                HabrApiSource(),
            ),
            customRssSource = customRssSource,
            preferencesRepository = preferencesRepository,
        )
        return ReaderInteractor(
            repository = repository,
            preferencesRepository = preferencesRepository,
            getFeeds = GetFeedsUseCase(repository),
            refreshFeed = RefreshFeedUseCase(repository),
            openArticle = OpenArticleUseCase(repository),
            toggleBookmark = ToggleBookmarkUseCase(repository),
            loadNextPage = LoadNextPageUseCase(repository),
            hasMorePages = HasMorePagesUseCase(repository),
        )
    }

    fun close() {
        httpClient.close()
    }
}
