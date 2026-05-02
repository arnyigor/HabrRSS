package com.arny.habrrss

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI

@Composable
actual fun rememberArticleActions(): ArticleActions = remember { DesktopArticleActions }

private object DesktopArticleActions : ArticleActions {
    override fun openUrl(url: String): Boolean {
        return runCatching {
            if (!Desktop.isDesktopSupported()) return false
            val desktop = Desktop.getDesktop()
            if (!desktop.isSupported(Desktop.Action.BROWSE)) return false
            desktop.browse(URI(url))
            true
        }.getOrDefault(false)
    }

    override fun shareText(text: String): Boolean = copyText(text)

    override fun copyText(text: String): Boolean {
        return runCatching {
            val selection = StringSelection(text)
            Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
            true
        }.getOrDefault(false)
    }
}
