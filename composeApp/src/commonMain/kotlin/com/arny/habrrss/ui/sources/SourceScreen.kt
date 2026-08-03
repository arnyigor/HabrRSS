package com.arny.habrrss.ui.sources

import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.AndroidUiModes.UI_MODE_NIGHT_YES
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arny.habrrss.domain.models.FeedDescriptor
import com.arny.habrrss.domain.models.FeedKind

@Composable
internal fun SourceScreen(
    feeds: List<FeedDescriptor>,
    activeFeedId: String?,
    favoriteTags: List<Pair<String, String>>,
    onFeedSelected: (String) -> Unit,
    onCustomFeedSaved: (String?, String, String) -> Unit,
    onCustomFeedRemoved: (String) -> Unit,
    onFavoriteTagRemoved: (String) -> Unit,
) {
    var editingId by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("") }
    var hub by remember { mutableStateOf("") }
    val hubFeeds = feeds.filter { it.kind == FeedKind.Hub || it.kind == FeedKind.Custom }
    var showHint by remember { mutableStateOf(true) }

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
                Column(
                    Modifier
                        .padding(16.dp)
                        .animateContentSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Хабы", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        IconButton(onClick = { showHint = !showHint }) {
                            Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "Подсказка")
                        }
                    }
                    if (showHint) {
                        Text(
                            "Введите только slug хаба на Хабре, например: android_dev, kotlin, machine_learning. " +
                                    "Лента будет загружаться через Habr API по 100 статей на страницу.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            label = { Text("Slug хаба") },
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
        }

        if (favoriteTags.isNotEmpty()) {
            item {
                Text(
                    "Избранные теги",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            items(favoriteTags, key = { it.first }) { (tagId, title) ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "#$title",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium
                        )
                        OutlinedButton(onClick = { onFavoriteTagRemoved(tagId) }) { Text("Удалить") }
                    }
                }
            }
        }

        item {
            Text(
                "Добавленные хабы",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
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
                    Text(
                        feed.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(feed.description, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        feed.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { onFeedSelected(feed.id) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "Открыть",
                            )
                        }
                        IconButton(
                            onClick = {
                                editingId = feed.id
                                title = feed.title
                                hub = feed.url.toHubSlug()
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "Изменить",
                            )
                        }
                        IconButton(onClick = { onCustomFeedRemoved(feed.id) }) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Удалить",
                                tint = MaterialTheme.colorScheme.error,
                            )
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

/**
 * Моковые данные для превью SourceScreen.
 * Подставьте реальные поля FeedDescriptor/FeedKind, если сигнатура отличается.
 */
private val previewHubFeeds = listOf(
    FeedDescriptor(
        id = "hub_android_dev",
        title = "Разработка под Android",
        description = "Статьи про Android, Kotlin и Compose",
        url = "https://habr.com/ru/hubs/android_dev/",
        kind = FeedKind.Hub,
        sourceTitle = "title",
    ),
    FeedDescriptor(
        id = "hub_kotlin",
        title = "Kotlin",
        description = "Всё о языке Kotlin и его экосистеме",
        url = "https://habr.com/ru/hubs/kotlin/",
        kind = FeedKind.Hub,
        sourceTitle = "title",
    ),
    FeedDescriptor(
        id = "custom_ml",
        title = "Machine Learning (мой)",
        description = "Пользовательский фид по машинному обучению",
        url = "https://habr.com/ru/hubs/machine_learning/",
        kind = FeedKind.Custom,
        sourceTitle = "title",
    ),
)

private val previewFavoriteHubs = listOf(
    "hub_gamedev" to "Game Development",
    "hub_unreal_engine" to "Unreal Engine",
)

private val previewFavoriteTags = listOf(
    "tag_llm" to "LLM",
    "tag_kmp" to "Kotlin Multiplatform",
)

private class SourceScreenPreviewProvider : PreviewParameterProvider<SourceScreenPreviewState> {
    override val values = sequenceOf(
        SourceScreenPreviewState(
            name = "Заполненный экран",
            feeds = previewHubFeeds,
            activeFeedId = "hub_android_dev",
            favoriteHubs = previewFavoriteHubs,
            favoriteTags = previewFavoriteTags,
        ),
        SourceScreenPreviewState(
            name = "Пустой экран",
            feeds = emptyList(),
            activeFeedId = null,
            favoriteHubs = emptyList(),
            favoriteTags = emptyList(),
        ),
        SourceScreenPreviewState(
            name = "Только избранное, без своих хабов",
            feeds = emptyList(),
            activeFeedId = null,
            favoriteHubs = previewFavoriteHubs,
            favoriteTags = previewFavoriteTags,
        ),
    )
}

private data class SourceScreenPreviewState(
    val name: String,
    val feeds: List<FeedDescriptor>,
    val activeFeedId: String?,
    val favoriteHubs: List<Pair<String, String>>,
    val favoriteTags: List<Pair<String, String>>,
)

@Preview(
    name = "SourceScreen",
    showBackground = true,
    widthDp = 380,
    heightDp = 900,
)
@Composable
private fun SourceScreenPreview(
    @PreviewParameter(SourceScreenPreviewProvider::class) state: SourceScreenPreviewState,
) {
    MaterialTheme {
        Surface {
            SourceScreen(
                feeds = state.feeds,
                activeFeedId = state.activeFeedId,
                favoriteTags = state.favoriteTags,
                onFeedSelected = {},
                onCustomFeedSaved = { _, _, _ -> },
                onCustomFeedRemoved = {},
                onFavoriteTagRemoved = {},
            )
        }
    }
}

@Preview(
    name = "SourceScreen - Dark",
    showBackground = true,
    widthDp = 380,
    heightDp = 900,
    uiMode = UI_MODE_NIGHT_YES,
)
@Composable
private fun SourceScreenDarkPreview() {
    MaterialTheme {
        Surface {
            SourceScreen(
                feeds = previewHubFeeds,
                activeFeedId = previewHubFeeds.first().id,
                favoriteTags = previewFavoriteTags,
                onFeedSelected = {},
                onCustomFeedSaved = { _, _, _ -> },
                onCustomFeedRemoved = {},
                onFavoriteTagRemoved = {},
            )
        }
    }
}
