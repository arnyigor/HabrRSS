package com.arny.habrrss.ui.sources

import androidx.compose.foundation.clickable
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
    onFeedSelected: (String) -> Unit,
    onCustomFeedSaved: (String?, String, String) -> Unit,
    onCustomFeedRemoved: (String) -> Unit,
) {
    var editingId by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

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
                    Text("Свои RSS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Вставьте прямую ссылку на RSS/Atom. Для Хабра удобно использовать URL вида: " +
                            "https://habr.com/ru/rss/hub/android/?limit=100&with_hubs=true&with_tags=true",
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
                        value = url,
                        onValueChange = { url = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("RSS URL") },
                        placeholder = { Text("https://example.com/rss.xml") },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = url.isNotBlank(),
                            onClick = {
                                onCustomFeedSaved(editingId, title, url)
                                editingId = null
                                title = ""
                                url = ""
                            },
                        ) {
                            Text(if (editingId == null) "Добавить RSS" else "Сохранить")
                        }
                        if (editingId != null) {
                            OutlinedButton(
                                onClick = {
                                    editingId = null
                                    title = ""
                                    url = ""
                                },
                            ) {
                                Text("Отмена")
                            }
                        }
                    }
                }
            }
        }

        items(feeds, key = { it.id }) { feed ->
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onFeedSelected(feed.id) },
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
                    if (feed.kind == FeedKind.Custom) {
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    editingId = feed.id
                                    title = feed.title
                                    url = feed.url
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
}
