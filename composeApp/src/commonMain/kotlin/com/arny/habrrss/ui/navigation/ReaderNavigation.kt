package com.arny.habrrss.ui.navigation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.arny.habrrss.navigation.Screen
import com.arny.habrrss.presentation.ReaderDestination
import com.arny.habrrss.presentation.ReaderUiState

// Mapping of screens to navigation items
private val bottomNavItems = listOf(
    Triple(Screen.Feed, "Лента", Icons.AutoMirrored.Filled.Article),
    Triple(Screen.Sources, "Хабы", Icons.Filled.RssFeed),
    Triple(Screen.Bookmarks, "Сохр.", Icons.Filled.Bookmark),
    Triple(Screen.Settings, "Ещё", Icons.Filled.Settings),
)

@Composable
internal fun ReaderRail(
    selectedTopLevel: Screen,
    onDestinationSelected: (ReaderDestination) -> Unit,
    onScreenSelected: (Screen) -> Unit,
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
            val isSelected = selectedTopLevel == screen
            NavigationRailItem(
                selected = isSelected,
                onClick = {
                    onDestinationSelected(screen.toDestination())
                    onScreenSelected(screen)
                },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
internal fun ReaderBottomBar(
    selectedTopLevel: Screen,
    onDestinationSelected: (ReaderDestination) -> Unit,
    onScreenSelected: (Screen) -> Unit,
) {
    NavigationBar {
        bottomNavItems.forEach { (screen, label, icon) ->
            val isSelected = selectedTopLevel == screen
            NavigationBarItem(
                selected = isSelected,
                onClick = {
                    onDestinationSelected(screen.toDestination())
                    onScreenSelected(screen)
                },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label, maxLines = 1) },
            )
        }
    }
}

internal fun Screen.toDestination(): ReaderDestination = when (this) {
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
    onSearchChanged: (String) -> Unit,
) {
    TopAppBar(
        title = {
            if (state.selectedDestination == ReaderDestination.Feed || state.selectedDestination == ReaderDestination.Bookmarks) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onSearchChanged,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    placeholder = { Text("Поиск по статьям") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.6f),
                        focusedLeadingIconColor = Color.White,
                        unfocusedLeadingIconColor = Color.White.copy(alpha = 0.75f),
                        focusedPlaceholderColor = Color.White.copy(alpha = 0.75f),
                        unfocusedPlaceholderColor = Color.White.copy(alpha = 0.75f),
                    ),
                )
            } else {
                Text(
                    text = "TechReader",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color(0xFF26323B), // Habr dark
            titleContentColor = Color.White,
            actionIconContentColor = Color.White,
        ),
    )
}
