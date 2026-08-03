package com.arny.habrrss.ui.sources

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arny.habrrss.domain.models.FeedDescriptor
import com.arny.habrrss.domain.models.FeedKind

@Composable
internal fun SourceScreen(
    feeds: List<FeedDescriptor>,
    activeFeedId: String?,
    favoriteHubs: List<Pair<String, String>>,
    favoriteTags: List<Pair<String, String>>,
    onFeedSelected: (String) -> Unit,
    onCustomFeedSaved: (String?, String, String) -> Unit,
    onCustomFeedRemoved: (String) -> Unit,
    onFavoriteHubRemoved: (String) -> Unit,
    onFavoriteTagRemoved: (String) -> Unit,
) {
    var editingId by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("") }
    var hub by remember { mutableStateOf("") }
    val hubFeeds = feeds.filter { it.kind == FeedKind.Hub || it.kind == FeedKind.Custom }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ElevatedCard(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Хабы", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Введите только slug хаба на Хабре, например: android_dev, kotlin, machine_learning. " +
                            "Лента будет загружаться через Habr API по 100 статей на страницу.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Название") },
                    )
                    OutlinedTextField(
                        value = hub,
                        onValueChange = { hub = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Хаб") },
                        placeholder = { Text("android") },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = hub.isNotBlank(),
                            onClick = {
                                onCustomFeedSaved(editingId, title, hub)
                                editingId = null
                                title = ""
                                hub = ""
                            },
                        ) {
                            Text(if (editingId == null) "Добавить хаб" else "Сохранить")
                        }
                        if (editingId != null) {
                            OutlinedButton(
                                onClick = {
                                    editingId = null
                                    title = ""
                                    hub = ""
                                },
                            ) {
                                Text("Отмена")
                            }
                        }
                    }
                }
            }
        }

        if (favoriteHubs.isNotEmpty()) {
            item {
                Text("Избранные хабы", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            items(favoriteHubs, key = { it.first }) { (hubId, title) ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        OutlinedButton(onClick = { onFavoriteHubRemoved(hubId) }) { Text("Удалить") }
                    }
                }
            }
        }

        if (favoriteTags.isNotEmpty()) {
            item {
                Text("Избранные теги", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            items(favoriteTags, key = { it.first }) { (tagId, title) ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("#$title", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        OutlinedButton(onClick = { onFavoriteTagRemoved(tagId) }) { Text("Удалить") }
                    }
                }
            }
        }

        item {
            Text("Добавленные хабы", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        items(hubFeeds, key = { it.id }) { feed ->
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = if (feed.id == activeFeedId) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                ),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(feed.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(feed.description, style = MaterialTheme.typography.bodyMedium)
                    Text(feed.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onFeedSelected(feed.id) }) {
                            Text("Открыть")
                        }
                        OutlinedButton(
                            onClick = {
                                editingId = feed.id
                                title = feed.title
                                hub = feed.url.toHubSlug()
                            },
                        ) {
                            Text("Изменить")
                        }
                        OutlinedButton(onClick = { onCustomFeedRemoved(feed.id) }) {
                            Text("Удалить")
                        }
                    }
                }
            }
        }
    }
}

private fun String.toHubSlug(): String {
    val value = trim().trimEnd('/')
    val slug = when {
        "/hubs/" in value -> value.substringAfterLast("/hubs/")
        "/hub/" in value -> value.substringAfterLast("/hub/")
        else -> value
    }
    return slug
        .substringBefore('/')
        .substringBefore('?')
        .trim()
}
