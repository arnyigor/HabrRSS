package com.arny.habrrss.ui.article

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
internal actual fun ArticleScrollContainer(
    modifier: Modifier,
    state: LazyListState,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier) {
        content()
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(state),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(),
        )
    }
}
