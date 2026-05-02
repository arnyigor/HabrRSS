package com.arny.habrrss

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arny.habrrss.presentation.ReaderDestination
import com.arny.habrrss.presentation.ReaderUiState

@Composable
internal fun ReaderRail(
    state: ReaderUiState,
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
        state.destinations.forEach { destination ->
            NavigationRailItem(
                selected = state.selectedDestination == destination,
                onClick = { onDestinationSelected(destination) },
                icon = { DestinationIcon(destination) },
                label = { Text(destination.label) },
            )
        }
    }
}

@Composable
internal fun ReaderBottomBar(
    state: ReaderUiState,
    onDestinationSelected: (ReaderDestination) -> Unit,
) {
    NavigationBar {
        state.destinations.forEach { destination ->
            NavigationBarItem(
                selected = state.selectedDestination == destination,
                onClick = { onDestinationSelected(destination) },
                icon = { DestinationIcon(destination) },
                label = { Text(destination.label, maxLines = 1) },
            )
        }
    }
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
                    text = "${state.selectedFeedTitle} · ${state.visibleItems.size} статей · ${state.cachePolicyLabel}",
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
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

@Composable
private fun DestinationIcon(destination: ReaderDestination) {
    val icon = when (destination) {
        ReaderDestination.Feed -> Icons.AutoMirrored.Filled.Article
        ReaderDestination.Sources -> Icons.Filled.RssFeed
        ReaderDestination.Bookmarks -> Icons.Filled.Bookmark
        ReaderDestination.Search -> Icons.Filled.Search
        ReaderDestination.Settings -> Icons.Filled.Settings
    }
    Icon(icon, contentDescription = destination.label)
}
