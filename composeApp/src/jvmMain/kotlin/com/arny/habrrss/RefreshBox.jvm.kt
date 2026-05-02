package com.arny.habrrss

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun RefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier,
    content: @Composable () -> Unit
) {
    // On desktop, pull-to-refresh is not needed - refresh button is in TopAppBar
    Box(modifier = modifier) {
        content()
    }
}