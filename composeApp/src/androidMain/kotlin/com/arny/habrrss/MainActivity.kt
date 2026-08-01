package com.arny.habrrss

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import com.arny.habrrss.data.database.appContext
import com.arny.habrrss.di.initKoin
import com.arny.habrrss.ui.App

class MainActivity : ComponentActivity() {
    private var articleUrl by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(HabrSystemBarColor),
            navigationBarStyle = SystemBarStyle.dark(HabrSystemBarColor),
        )
        super.onCreate(savedInstanceState)
        appContext = applicationContext
        initKoin()
        articleUrl = intent?.habrArticleUrl()

        setContent {
            App(initialArticleUrl = articleUrl)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        articleUrl = intent.habrArticleUrl()
    }

    private fun Intent.habrArticleUrl(): String? {
        if (action != Intent.ACTION_VIEW) return null
        return dataString?.takeIf { url ->
            url.contains("habr.com", ignoreCase = true) &&
                (url.contains("/articles/") || url.contains("/posts/") || url.contains("/news/"))
        }
    }
}

private val HabrSystemBarColor: Int = Color.rgb(0x26, 0x32, 0x3B)

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
