package com.arny.habrrss.ui.article

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arny.habrrss.domain.models.ArticleBlock
import com.arny.habrrss.domain.models.ArticleContent
import com.arny.habrrss.domain.models.FeedSettings
import com.arny.habrrss.ui.components.EmptyState

@Composable
internal fun ArticleScreen(
    modifier: Modifier,
    article: ArticleContent?,
    showBack: Boolean,
    settings: FeedSettings,
    favoriteTagIds: Set<String>,
    favoriteHubIds: Set<String>,
    onBack: () -> Unit,
    onHubSelected: (String) -> Unit,
    onFavoriteHubToggled: (String) -> Unit,
    onTagSelected: (String) -> Unit,
    onFavoriteTagToggled: (String) -> Unit,
) {
    if (article == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                title = "Выберите статью",
                message = "Откройте материал из ленты. На desktop статья появится справа, на телефоне откроется отдельным экраном.",
            )
        }
        return
    }

    // Fallback для случаев, когда RSS даёт только краткое описание
    val hasContent = article.blocks.isNotEmpty() && article.blocks.any { block ->
        when (block) {
            is ArticleBlock.Paragraph -> block.inline.isNotEmpty()
            is ArticleBlock.Heading -> block.inline.isNotEmpty()
            is ArticleBlock.CodeBlock -> block.code.isNotBlank()
            is ArticleBlock.Image -> block.url.isNotBlank()
            is ArticleBlock.ListBlock -> block.items.isNotEmpty()
            is ArticleBlock.Quote -> block.blocks.isNotEmpty()
            is ArticleBlock.Spoiler -> block.blocks.isNotEmpty()
            is ArticleBlock.TableBlock -> block.rows.isNotEmpty()
            is ArticleBlock.UnknownHtml -> block.html.isNotBlank()
        }
    }

    if (!hasContent) {
        ArticleFallbackView(
            article = article,
            showBack = showBack,
            onBack = onBack,
            modifier = modifier,
        )
        return
    }

    val actions = rememberArticleActions()
    val articleListState = rememberLazyListState()
    ArticleScrollContainer(
        modifier = modifier.fillMaxSize(),
        state = articleListState,
    ) {
        LazyColumn(
            state = articleListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                ArticleHeader(
                    article = article,
                    showBack = showBack,
                    favoriteTagIds = favoriteTagIds,
                    favoriteHubIds = favoriteHubIds,
                    onBack = onBack,
                    onHubSelected = onHubSelected,
                    onFavoriteHubToggled = onFavoriteHubToggled,
                    onTagSelected = onTagSelected,
                    onFavoriteTagToggled = onFavoriteTagToggled,
                )
            }
            item {
                ArticleToolbar(article)
            }
            item {
                ArticleSourceNotice(article.sourceNotice)
            }
            items(article.blocks) { block ->
                ArticleBlockView(
                    block = block,
                    settings = settings,
                    modifier = Modifier.widthIn(max = 860.dp),
                    onLinkClick = { url -> actions.openUrl(url) },
                )
            }
            item {
                ArticleFooterSections(
                    article = article,
                    favoriteTagIds = favoriteTagIds,
                    onTagSelected = onTagSelected,
                    onFavoriteTagToggled = onFavoriteTagToggled,
                    modifier = Modifier.widthIn(max = 860.dp),
                )
            }
        }
    }
}

@Composable
private fun ArticleFallbackView(
    article: ArticleContent,
    showBack: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val actions = rememberArticleActions()
    val validUrl = article.url.normalizedExternalUrl()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (showBack) {
            TextButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("К ленте")
            }
        }

        Text(
            text = article.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        article.author?.displayName?.let { author ->
            Text(
                text = author,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(8.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = "RSS содержит только краткое описание",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Для чтения полной статьи откройте оригинал.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        if (validUrl != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { actions.openUrl(validUrl) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Открыть оригинал")
                }
            }
        }

        article.hubs.forEach { hub ->
            AssistChip(
                onClick = {},
                modifier = Modifier.widthIn(max = 220.dp),
                label = { Text(hub.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}
