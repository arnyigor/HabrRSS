package com.arny.habrrss.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Type-Safe navigation routes for the application.
 * Uses kotlinx.serialization for deep linking support.
 */
@Serializable
sealed class Screen : NavKey {
    @Serializable
    data object Feed : Screen()

    @Serializable
    data object Sources : Screen()

    @Serializable
    data object Bookmarks : Screen()

    @Serializable
    data object Search : Screen()

    @Serializable
    data object Settings : Screen()

    @Serializable
    data class Article(val articleId: String, val articleUrl: String? = null) : Screen()
}
