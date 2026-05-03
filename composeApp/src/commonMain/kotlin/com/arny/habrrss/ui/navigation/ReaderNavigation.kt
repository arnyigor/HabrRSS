package com.arny.habrrss.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.arny.habrrss.navigation.Screen
import com.arny.habrrss.presentation.ReaderDestination
import com.arny.habrrss.presentation.ReaderUiState

// Mapping of screens to navigation items
private val bottomNavItems = listOf(
    Triple(Screen.Feed, "Лента", Icons.AutoMirrored.Filled.Article),
    Triple(Screen.Sources, "RSS", Icons.Filled.RssFeed),
    Triple(Screen.Bookmarks, "Сохр.", Icons.Filled.Bookmark),
    Triple(Screen.Search, "Поиск", Icons.Filled.Search),
    Triple(Screen.Settings, "Ещё", Icons.Filled.Settings)
)

@Composable
internal fun ReaderRail(
    state: ReaderUiState,
    navController: NavController,
    currentRoute: Any?,
    onDestinationSelected: (ReaderDestination) -> Unit,
) {
    NavigationRail(
        header = {
            Text(
                text = "TR",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
    ) {
        bottomNavItems.forEach { (screen, label, icon) ->
            val isSelected = currentRoute == screen
            NavigationRailItem(
                selected = isSelected,
                onClick = {
                    onDestinationSelected(screen.toDestination())
                    navController.navigate(screen) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            inclusive = false
                        }
                        launchSingleTop = true
                    }
                },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
internal fun ReaderBottomBar(
    navController: NavController,
    currentRoute: Any?,
    onDestinationSelected: (ReaderDestination) -> Unit,
) {
    NavigationBar {
        bottomNavItems.forEach { (screen, label, icon) ->
            val isSelected = currentRoute == screen
            NavigationBarItem(
                selected = isSelected,
                onClick = {
                    onDestinationSelected(screen.toDestination())
                    navController.navigate(screen) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            inclusive = false
                        }
                        launchSingleTop = true
                    }
                },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label, maxLines = 1) },
            )
        }
    }
}

private fun Screen.toDestination(): ReaderDestination = when (this) {
    Screen.Feed -> ReaderDestination.Feed
    Screen.Sources -> ReaderDestination.Sources
    Screen.Bookmarks -> ReaderDestination.Bookmarks
    Screen.Search -> ReaderDestination.Search
    Screen.Settings -> ReaderDestination.Settings
    is Screen.Article -> ReaderDestination.Feed
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderTopBar(
    state: ReaderUiState,
    onRefresh: () -> Unit,
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = "TechReader",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${state.selectedFeedTitle} - ${state.visibleItems.size} статей",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        actions = {
            if (state.activeFilterCount > 0) {
                Text(
                    text = "${state.activeFilterCount}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
            }
            IconButton(
                onClick = onRefresh,
                enabled = !state.isRefreshing,
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = "Обновить")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color(0xFF26323B), // Habr dark
            titleContentColor = Color.White,
            actionIconContentColor = Color.White,
        ),
    )
}
