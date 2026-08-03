package com.arny.habrrss.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.arny.habrrss.domain.models.FeedSettings
import com.arny.habrrss.domain.models.ThemeMode
import com.arny.habrrss.domain.sync.BackgroundSyncManager
import com.arny.habrrss.presentation.ArticleIntent
import com.arny.habrrss.presentation.ArticleViewModel
import com.arny.habrrss.presentation.FeedIntent
import com.arny.habrrss.presentation.FeedViewModel
import com.arny.habrrss.ui.navigation.ReaderApp
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun App(initialArticleUrl: String? = null) {
    val viewModel = koinViewModel<FeedViewModel>()
    val articleViewModel = koinViewModel<ArticleViewModel>()
    val syncManager = koinInject<BackgroundSyncManager>()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(initialArticleUrl) {
        if (!initialArticleUrl.isNullOrBlank()) {
            viewModel.dispatch(FeedIntent.OpenArticleUrl(initialArticleUrl))
            articleViewModel.dispatch(ArticleIntent.OpenUrl(initialArticleUrl))
        }
    }

    DisposableEffect(syncManager) {
        syncManager.startPeriodicSync()
        onDispose {
            syncManager.stopSync()
        }
    }

    ReaderTheme(settings = state.settings) {
        ReaderApp(state = state, viewModel = viewModel, articleViewModel = articleViewModel)
    }
}

@Composable
fun ReaderTheme(
    settings: FeedSettings,
    content: @Composable () -> Unit,
) {
    val useDarkColors = when (settings.themeMode) {
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
        ThemeMode.System -> isSystemInDarkTheme()
    }

    MaterialTheme(
        colorScheme = if (useDarkColors) darkColorScheme() else lightColorScheme(),
        content = content,
    )
}
