package com.arny.habrrss.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import com.arny.habrrss.domain.sync.BackgroundSyncManager
import com.arny.habrrss.presentation.FeedViewModel
import com.arny.habrrss.ui.navigation.ReaderApp
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
@Preview
fun App() {
    val viewModel = koinViewModel<FeedViewModel>()
    val syncManager = koinInject<BackgroundSyncManager>()
    val state by viewModel.state.collectAsState()

    DisposableEffect(syncManager) {
        syncManager.startPeriodicSync()
        onDispose {
            syncManager.stopSync()
        }
    }

    MaterialTheme {
        ReaderApp(state = state, viewModel = viewModel)
    }
}
