package com.arny.habrrss.ui.article

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberArticleActions(): ArticleActions {
    val context = LocalContext.current
    return remember(context) { AndroidArticleActions(context) }
}

private class AndroidArticleActions(
    private val context: Context,
) : ArticleActions {
    override fun openUrl(url: String): Boolean {
        val uri = Uri.parse(url)
        return if (uri.shouldPreferBrowser()) {
            openInBrowser(uri)
        } else {
            openWithSystemHandler(uri) || openInBrowser(uri)
        }
    }

    override fun shareText(text: String): Boolean {
        return runCatching {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            val chooser = Intent.createChooser(sendIntent, null).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        }.getOrDefault(false)
    }

    override fun copyText(text: String): Boolean {
        return runCatching {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("HabrRSS", text))
            true
        }.getOrDefault(false)
    }

    private fun openWithSystemHandler(uri: Uri): Boolean {
        return runCatching {
            context.startActivity(uri.viewIntent())
            true
        }.getOrDefault(false)
    }

    private fun openInBrowser(uri: Uri): Boolean {
        return KnownBrowserPackages.any { packageName ->
            openInPackage(uri, packageName)
        } || context.findBrowserPackages().any { packageName ->
            openInPackage(uri, packageName)
        }
    }

    private fun openInPackage(uri: Uri, packageName: String): Boolean {
        return runCatching {
            context.startActivity(uri.viewIntent().setPackage(packageName))
            true
        }.getOrDefault(false)
    }

    private fun Uri.viewIntent(): Intent =
        Intent(Intent.ACTION_VIEW, this).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    private fun Uri.shouldPreferBrowser(): Boolean {
        val normalizedHost = host?.lowercase()?.removePrefix("www.") ?: return false
        return normalizedHost in BrowserFirstHosts
    }

    private fun Context.findBrowserPackages(): List<String> {
        val probe = Uri.parse("https://www.example.com/").viewIntent()
        return packageManager.queryIntentActivitiesCompat(probe)
            .asSequence()
            .mapNotNull { it.activityInfo?.packageName }
            .filter { it != packageName }
            .filter { it !in NonBrowserPackages }
            .distinct()
            .toList()
    }

    private fun PackageManager.resolveActivityCompat(intent: Intent) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            resolveActivity(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
            @Suppress("DEPRECATION")
            resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }

    private fun PackageManager.queryIntentActivitiesCompat(intent: Intent) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
        } else {
            @Suppress("DEPRECATION")
            queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }

    private companion object {
        const val ANDROID_PACKAGE = "android"

        val KnownBrowserPackages = listOf(
            "com.android.chrome",
            "com.chrome.beta",
            "com.chrome.dev",
            "com.chrome.canary",
            "org.mozilla.firefox",
            "org.mozilla.firefox_beta",
            "com.microsoft.emmx",
            "com.brave.browser",
            "com.sec.android.app.sbrowser",
            "com.opera.browser",
            "com.opera.mini.native",
            "com.duckduckgo.mobile.android",
            "com.yandex.browser",
            "com.yandex.browser.beta",
            "com.yandex.browser.alpha",
            "com.huawei.browser",
            "com.mi.globalbrowser",
            "com.android.browser",
        )

        val NonBrowserPackages = setOf(
            ANDROID_PACKAGE,
            "com.google.android.apps.docs",
            "com.google.android.apps.docs.editors.docs",
            "com.google.android.apps.docs.editors.sheets",
            "com.google.android.apps.docs.editors.slides",
        )

        val BrowserFirstHosts = setOf(
            "drive.google.com",
            "docs.google.com",
            "sheets.google.com",
            "slides.google.com",
            "forms.google.com",
        )
    }
}
