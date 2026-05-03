package com.arny.habrrss

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.arny.habrrss.di.initKoin
import com.arny.habrrss.ui.App

fun main() = application {
    initKoin()

    Window(
        onCloseRequest = ::exitApplication,
        title = "TechReader",
    ) {
        App()
    }
}
