package com.arny.habrrss.ui.article

import androidx.compose.runtime.Composable

interface ArticleActions {
    fun openUrl(url: String): Boolean
    fun shareText(text: String): Boolean
    fun copyText(text: String): Boolean
}

@Composable
expect fun rememberArticleActions(): ArticleActions
