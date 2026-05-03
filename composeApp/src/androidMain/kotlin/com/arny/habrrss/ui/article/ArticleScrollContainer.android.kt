package com.arny.habrrss.ui.article

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal actual fun ArticleScrollContainer(
    modifier: Modifier,
    state: LazyListState,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier) {
        content()
    }
}
