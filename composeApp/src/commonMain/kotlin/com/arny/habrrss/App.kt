package com.arny.habrrss

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.arny.habrrss.di.AppGraph

@Composable
@Preview
fun App() {
    val presenter = remember { AppGraph.createReaderPresenter() }
    val state by presenter.state.collectAsState()

    LaunchedEffect(presenter) {
        presenter.start()
    }

    MaterialTheme {
        ReaderApp(state = state, presenter = presenter)
    }
}
