package com.arny.habrrss.di

import com.arny.habrrss.core.network.createHttpClient
import com.arny.habrrss.data.api.HabrApiSource
import com.arny.habrrss.data.article.HabrArticleContentSource
import com.arny.habrrss.data.database.createPlatformFeedDao
import com.arny.habrrss.data.repository.TechReaderRepository
import com.arny.habrrss.data.rss.GenericRssSource
import com.arny.habrrss.data.rss.HabrRssSource
import com.arny.habrrss.domain.sync.createBackgroundSyncManager
import com.arny.habrrss.domain.usecases.GetFeedsUseCase
import com.arny.habrrss.domain.usecases.HasMorePagesUseCase
import com.arny.habrrss.domain.usecases.LoadNextPageUseCase
import com.arny.habrrss.domain.usecases.OpenArticleUseCase
import com.arny.habrrss.domain.usecases.RefreshFeedUseCase
import com.arny.habrrss.domain.usecases.ToggleBookmarkUseCase
import com.arny.habrrss.presentation.ArticleViewModel
import com.arny.habrrss.presentation.FeedViewModel
import com.arny.habrrss.presentation.ReaderPresenter
import io.ktor.client.HttpClient
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single<HttpClient> { createHttpClient(enableLogging = false) }
    single { createPlatformFeedDao() }
    single { HabrArticleContentSource(get()) }
    single {
        TechReaderRepository(
            primarySource = HabrRssSource(get()),
            feedDao = get(),
            articleContentSource = get<HabrArticleContentSource>(),
            secondarySources = listOf(
                GenericRssSource(),
                HabrApiSource(),
            ),
        )
    }
    factory { GetFeedsUseCase(get()) }
    factory { RefreshFeedUseCase(get()) }
    factory { OpenArticleUseCase(get()) }
    factory { ToggleBookmarkUseCase(get()) }
    factory { LoadNextPageUseCase(get()) }
    factory { HasMorePagesUseCase(get()) }
    factory {
        ReaderPresenter(
            repository = get(),
            getFeeds = get(),
            refreshFeed = get(),
            openArticle = get(),
            toggleBookmark = get(),
            loadNextPage = get(),
            hasMorePages = get(),
        )
    }
    single { createBackgroundSyncManager(get()) }
    viewModel { FeedViewModel(get()) }
    viewModel { ArticleViewModel(get(), get()) }
}

fun initKoin() {
    if (GlobalContext.getOrNull() != null) return
    startKoin {
        modules(appModule)
    }
}
