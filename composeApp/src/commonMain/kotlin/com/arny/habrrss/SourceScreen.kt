package com.arny.habrrss

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arny.habrrss.domain.models.FeedDescriptor

@Composable
internal fun SourceScreen(
    feeds: List<FeedDescriptor>,
    activeFeedId: String?,
    onFeedSelected: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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
                }
            }
        }
    }
}
