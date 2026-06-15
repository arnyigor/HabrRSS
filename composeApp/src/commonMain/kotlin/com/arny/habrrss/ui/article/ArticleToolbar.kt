package com.arny.habrrss.ui.article

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arny.habrrss.domain.models.ArticleContent
import kotlinx.coroutines.delay

@Composable
internal fun ArticleToolbar(
    article: ArticleContent,
    isBookmarked: Boolean,
    onBookmark: () -> Unit,
) {
    val actions = rememberArticleActions()
    val validUrl = article.url.normalizedExternalUrl()
    val canOpen = validUrl != null
    var statusMessage by remember(article.id) { mutableStateOf<String?>(null) }

    // Auto-dismiss status message after 3 seconds
    LaunchedEffect(statusMessage) {
        if (statusMessage != null) {
            delay(3000)
            statusMessage = null
        }
    }

    fun openOriginal() {
        statusMessage = if (validUrl != null && actions.openUrl(validUrl)) {
            "Открываю оригинал"
        } else {
            "Не удалось открыть ссылку"
        }
    }

    fun copyShareText() {
        statusMessage = if (actions.shareText(article.shareText())) {
            "Поделиться: готово"
        } else {
            "Не удалось поделиться"
        }
    }

    fun copyMarkdown() {
        statusMessage = if (actions.copyText(article.markdownText())) {
            "Markdown скопирован"
        } else {
            "Не удалось скопировать Markdown"
        }
    }

    Column(
        modifier = Modifier.widthIn(max = 860.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onBookmark) {
                Icon(
                    if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                    contentDescription = null,
                )
                Spacer(Modifier.width(6.dp))
                Text(if (isBookmarked) "Сохранено" else "Сохранить")
            }
            if (canOpen) {
                OutlinedButton(onClick = { openOriginal() }) { Text("Оригинал") }
                OutlinedButton(onClick = { copyShareText() }) { Text("Поделиться") }
            } else {
                OutlinedButton(onClick = { openOriginal() }, enabled = false) { Text("Оригинал") }
                OutlinedButton(onClick = { copyShareText() }) { Text("Поделиться") }
            }
            OutlinedButton(onClick = { copyMarkdown() }) { Text("Markdown") }
        }
        statusMessage?.let { message ->
            Text(
                text = message,
                modifier = Modifier.padding(start = 2.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
