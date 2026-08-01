package com.arny.habrrss.ui.article

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arny.habrrss.domain.models.ArticleBlock
import com.arny.habrrss.domain.models.ArticleContent
import com.arny.habrrss.domain.models.CommentNode
import com.arny.habrrss.domain.models.FeedItem
import com.arny.habrrss.domain.models.FeedSettings
import com.arny.habrrss.ui.components.EmptyState


import androidx.compose.ui.tooling.preview.Preview
import com.arny.habrrss.domain.models.Author
import com.arny.habrrss.domain.models.Hub
import com.arny.habrrss.domain.models.InlineNode
import com.arny.habrrss.domain.models.Tag
import kotlinx.coroutines.launch

// ================= PREVIEW MOCK DATA =================

private fun createMockArticleContent(): ArticleContent = ArticleContent(
    id = "habr-123456",
    title = "Архитектура высоконагруженных систем в 2026 году",
    url = "https://habr.com/ru/articles/123456/",
    imageUrl = "https://habrastorage.org/cache/example.png",
    author = Author(
        id = "user-789",
        displayName = "SeniorArch",
        profileUrl = "https://habr.com/users/arch/"
    ),
    publishedAt = "Mon, 12 May 2026 10:30:00 GMT",
    tags = listOf(
        Tag("sys-arch", "Системная архитектура"),
        Tag("java", "Java"),
        Tag("distributed", "Распределённые системы")
    ),
    hubs = listOf(Hub("hub-k8s", "Кубернетис и микросервисы")),
    blocks = listOf(
        ArticleBlock.Heading(level = 2, inline = listOf(InlineNode.Text("Введение"))),
        ArticleBlock.Paragraph(inline = listOf(
            InlineNode.Text("В современных условиях требования к отказоустойчивости растут экспоненциально. "),
            InlineNode.Bold(children = listOf(InlineNode.Text("Резервирование"))),
            InlineNode.Text(" становится стандартом.")
        )),
        ArticleBlock.Image(url = "https://example.com/diagram.png", alt = "Схема взаимодействия"),
        ArticleBlock.CodeBlock(language = "kotlin", code = """
            fun handleRequest(req: Request): Response {
                return repository.process(req)
            }
        """.trimIndent()),
        ArticleBlock.Paragraph(inline = listOf(InlineNode.Link(text = "Ссылка на документацию", url = "https://docs.example.com"))),
        ArticleBlock.ListBlock(ordered = false, items = listOf(
            listOf(ArticleBlock.Paragraph(inline = listOf(InlineNode.Text("Отказоустойчивость")))),
            listOf(ArticleBlock.Paragraph(inline = listOf(InlineNode.Text("Горизонтальное масштабирование")))),
            listOf(ArticleBlock.Paragraph(inline = listOf(InlineNode.Text("Observability"))))
        ))
    ),
    sourceNotice = "Опубликовано на Хабре"
)

private fun createFallbackArticleContent(): ArticleContent = ArticleContent(
    id = "habr-fallback",
    title = "Статья с обрезанным контентом",
    url = "https://habr.com/ru/articles/fallback/",
    imageUrl = null,
    author = Author(id = "user-000", displayName = "Author", profileUrl = null),
    publishedAt = "Tue, 13 May 2026 08:00:00 GMT",
    tags = emptyList(),
    hubs = listOf(Hub("hub-tech", "Технологии")),
    blocks = listOf(ArticleBlock.Paragraph(inline = emptyList())), // Триггерит !hasContent
    sourceNotice = ""
)

// ================= PREVIEW COMPOSABLES =================

@Preview(name = "ArticleScreen - Empty State", showBackground = true)
@Composable
private fun ArticleScreenEmptyPreview() {
    ArticleScreen(
        modifier = Modifier,
        article = null,
        showBack = true,
        settings = FeedSettings.defaults(),
        favoriteTagIds = emptySet(),
        favoriteHubIds = emptySet(),
        onBack = { },
        onHubSelected = { },
        onFavoriteHubToggled = { },
        onTagSelected = { },
        onFavoriteTagToggled = { },
        isBookmarked = false,
        onBookmark = { },
    )
}

@Preview(name = "ArticleScreen - Full Content", showBackground = true)
@Composable
private fun ArticleScreenFullPreview() {
    ArticleScreen(
        modifier = Modifier,
        article = createMockArticleContent(),
        showBack = true,
        settings = FeedSettings.defaults(),
        favoriteTagIds = setOf("sys-arch"),
        favoriteHubIds = setOf("hub-k8s"),
        onBack = { },
        onHubSelected = { },
        onFavoriteHubToggled = { },
        onTagSelected = { },
        onFavoriteTagToggled = { },
        isBookmarked = true,
        onBookmark = { },
    )
}

@Preview(name = "ArticleScreen - Fallback View", showBackground = true)
@Composable
private fun ArticleScreenFallbackPreview() {
    ArticleScreen(
        modifier = Modifier,
        article = createFallbackArticleContent(),
        showBack = true,
        settings = FeedSettings.defaults(),
        favoriteTagIds = emptySet(),
        favoriteHubIds = emptySet(),
        onBack = { },
        onHubSelected = { },
        onFavoriteHubToggled = { },
        onTagSelected = { },
        onFavoriteTagToggled = { },
        isBookmarked = false,
        onBookmark = { },
    )
}


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
    isBookmarked: Boolean,
    onBookmark: () -> Unit,
    comments: List<CommentNode> = emptyList(),
    relatedArticles: List<FeedItem> = emptyList(),
    isLoadingExtras: Boolean = false,
    onRelatedArticleSelected: (String) -> Unit = {},
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
        Scaffold(
            modifier = modifier,
            topBar = {
                ArticleTopBar(
                    article = article,
                    showBack = showBack,
                    onBack = onBack,
                    isBookmarked = isBookmarked,
                    onBookmark = onBookmark,
                )
            },
        ) { innerPadding ->
            ArticleFallbackView(
                article = article,
                showBack = false,
                onBack = onBack,
                isBookmarked = isBookmarked,
                onBookmark = onBookmark,
                modifier = Modifier.padding(innerPadding),
            )
        }
        return
    }

    val actions = rememberArticleActions()
    val articleListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val showScrollToTop = (articleListState.firstVisibleItemIndex > 0 || articleListState.firstVisibleItemScrollOffset > 300) &&
        !articleListState.isScrollInProgress
    Scaffold(
        modifier = modifier,
        topBar = {
            ArticleTopBar(
                article = article,
                showBack = showBack,
                onBack = onBack,
                isBookmarked = isBookmarked,
                onBookmark = onBookmark,
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = showScrollToTop,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                FloatingActionButton(onClick = { coroutineScope.launch { articleListState.animateScrollToItem(0) } }) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Наверх")
                }
            }
        },
    ) { innerPadding ->
        ArticleScrollContainer(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
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
                    showBack = false,
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
                ArticleToolbar(
                    article = article,
                    isBookmarked = isBookmarked,
                    onBookmark = onBookmark,
                )
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
                    comments = comments,
                    relatedArticles = relatedArticles,
                    isLoadingExtras = isLoadingExtras,
                    onRelatedArticleSelected = onRelatedArticleSelected,
                    modifier = Modifier.widthIn(max = 860.dp),
                )
            }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArticleTopBar(
    article: ArticleContent,
    showBack: Boolean,
    onBack: () -> Unit,
    isBookmarked: Boolean,
    onBookmark: () -> Unit,
) {
    TopAppBar(
        navigationIcon = {
            if (showBack) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                }
            }
        },
        title = {
            Text(
                text = article.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
            )
        },
        actions = {
            IconButton(onClick = onBookmark) {
                Icon(
                    if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                    contentDescription = if (isBookmarked) "Убрать из закладок" else "Сохранить",
                )
            }
        },
    )
}

@Composable
private fun ArticleFallbackView(
    article: ArticleContent,
    showBack: Boolean,
    onBack: () -> Unit,
    isBookmarked: Boolean,
    onBookmark: () -> Unit,
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
                    onClick = onBookmark,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (isBookmarked) "Сохранено" else "Сохранить")
                }
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
