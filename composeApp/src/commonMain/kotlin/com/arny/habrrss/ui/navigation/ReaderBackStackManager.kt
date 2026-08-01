package com.arny.habrrss.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import com.arny.habrrss.navigation.Screen
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Navigation 3 back stack holder with independent stacks for top-level destinations.
 */
@Stable
internal class ReaderBackStackManager(
    initialTab: Screen,
    private val backStacks: Map<Screen, SnapshotStateList<Screen>>,
) {
    var currentTab by mutableStateOf(initialTab.topLevelScreen())
        private set

    val currentBackStack: SnapshotStateList<Screen>
        get() = backStacks.getValue(currentTab)

    val currentRoute: Screen
        get() = currentBackStack.lastOrNull() ?: currentTab

    fun selectTopLevel(screen: Screen) {
        val tab = screen.topLevelScreen()
        if (tab == currentTab) {
            popToRoot()
        } else {
            currentTab = tab
        }
    }

    fun navigate(screen: Screen) {
        val stack = currentBackStack
        if (stack.lastOrNull() != screen) {
            stack.add(screen)
        }
    }

    fun popBackStack(): Screen? {
        val stack = currentBackStack
        return if (stack.size > 1) {
            stack.removeAt(stack.lastIndex)
        } else {
            null
        }
    }

    fun popToRoot() {
        val stack = currentBackStack
        while (stack.size > 1) {
            stack.removeAt(stack.lastIndex)
        }
    }

    private fun snapshot(): ReaderBackStackData = ReaderBackStackData(
        currentTab = currentTab,
        feedStack = backStacks.stackFor(Screen.Feed),
        sourcesStack = backStacks.stackFor(Screen.Sources),
        bookmarksStack = backStacks.stackFor(Screen.Bookmarks),
        searchStack = backStacks.stackFor(Screen.Search),
        settingsStack = backStacks.stackFor(Screen.Settings),
    )

    private fun Map<Screen, SnapshotStateList<Screen>>.stackFor(tab: Screen): List<Screen> =
        getValue(tab).toList()

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        val Saver: Saver<ReaderBackStackManager, String> = Saver(
            save = { manager -> json.encodeToString(ReaderBackStackData.serializer(), manager.snapshot()) },
            restore = { value ->
                val data = json.decodeFromString(ReaderBackStackData.serializer(), value)
                ReaderBackStackManager(
                    initialTab = data.currentTab.topLevelScreen(),
                    backStacks = mutableStateMapOf(
                        Screen.Feed to data.feedStack.normalizedStack(Screen.Feed),
                        Screen.Sources to data.sourcesStack.normalizedStack(Screen.Sources),
                        Screen.Bookmarks to data.bookmarksStack.normalizedStack(Screen.Bookmarks),
                        Screen.Search to data.searchStack.normalizedStack(Screen.Search),
                        Screen.Settings to data.settingsStack.normalizedStack(Screen.Settings),
                    ),
                )
            },
        )

        private fun List<Screen>.normalizedStack(root: Screen): SnapshotStateList<Screen> =
            (if (isEmpty() || first() != root) listOf(root) else this).toMutableStateList()
    }
}

@Composable
internal fun rememberReaderBackStackManager(): ReaderBackStackManager =
    rememberSaveable(saver = ReaderBackStackManager.Saver) {
        ReaderBackStackManager(
            initialTab = Screen.Feed,
            backStacks = mutableStateMapOf(
                Screen.Feed to mutableStateListOf(Screen.Feed),
                Screen.Sources to mutableStateListOf(Screen.Sources),
                Screen.Bookmarks to mutableStateListOf(Screen.Bookmarks),
                Screen.Search to mutableStateListOf(Screen.Search),
                Screen.Settings to mutableStateListOf(Screen.Settings),
            ),
        )
    }

internal fun Screen.topLevelScreen(): Screen = when (this) {
    is Screen.Article -> Screen.Feed
    Screen.Feed,
    Screen.Sources,
    Screen.Bookmarks,
    Screen.Search,
    Screen.Settings -> this
}

@Serializable
private data class ReaderBackStackData(
    val currentTab: Screen,
    val feedStack: List<Screen>,
    val sourcesStack: List<Screen>,
    val bookmarksStack: List<Screen>,
    val searchStack: List<Screen>,
    val settingsStack: List<Screen>,
)
