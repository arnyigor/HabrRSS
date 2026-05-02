package com.arny.habrrss

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.arny.habrrss.domain.export.MarkdownExporter
import com.arny.habrrss.domain.models.ArticleBlock
import com.arny.habrrss.domain.models.ArticleContent
import com.arny.habrrss.domain.models.FeedSettings
import com.arny.habrrss.domain.models.Hub
import com.arny.habrrss.domain.models.InlineNode
import com.arny.habrrss.domain.models.Tag

internal const val LINK_TAG = "url"

@Composable
internal fun ArticleScreen(
    modifier: Modifier,
    article: ArticleContent?,
    showBack: Boolean,
    settings: FeedSettings,
    favoriteTagIds: Set<String>,
    onBack: () -> Unit,
    onHubSelected: (String) -> Unit,
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

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
                ArticleHeader(
                    article = article,
                    showBack = showBack,
                    favoriteTagIds = favoriteTagIds,
                    onBack = onBack,
                    onHubSelected = onHubSelected,
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
            )
        }
        item {
            ArticleFooterSections(
                article = article,
                modifier = Modifier.widthIn(max = 860.dp),
            )
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
                label = { Text(hub.title) },
            )
        }
    }
}

@Composable
private fun ArticleHeader(
    article: ArticleContent,
    showBack: Boolean,
    favoriteTagIds: Set<String>,
    onBack: () -> Unit,
    onHubSelected: (String) -> Unit,
    onTagSelected: (String) -> Unit,
    onFavoriteTagToggled: (String) -> Unit,
) {
    Column(Modifier.widthIn(max = 860.dp)) {
        if (showBack) {
            TextButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("К ленте")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = article.author?.displayName?.firstOrNull()?.uppercaseChar()?.toString() ?: "H",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = article.author?.displayName ?: "Habr RSS",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            humanReadableDate(article.publishedAt).takeIf { it.isNotBlank() }?.let { date ->
                Text(
                    text = "  $date",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = article.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Средний", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text("${article.estimatedReadingMinutes()} мин", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("RSS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            article.hubs.forEach { hub ->
                HubArticleChip(hub = hub, onClick = { onHubSelected(hub.id) })
            }
            article.tags.forEach { tag ->
                TagArticleChip(
                    tag = tag,
                    favorite = favoriteTagIds.contains(tag.id),
                    onClick = { onTagSelected(tag.id) },
                    onFavoriteClick = { onFavoriteTagToggled(tag.id) },
                )
            }
        }
    }
}

@Composable
private fun ArticleSourceNotice(
    text: String,
) {
    Surface(
        modifier = Modifier
            .widthIn(max = 860.dp)
            .fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(0.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun ArticleFooterSections(
    article: ArticleContent,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HorizontalDivider()
        if (article.tags.isNotEmpty()) {
            Text(
                text = "Теги: ${article.tags.joinToString { it.title }}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (article.hubs.isNotEmpty()) {
            Text(
                text = "Хабы: ${article.hubs.joinToString { it.title }}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ArticleAuthorCard(article)
        ArticleCommentsStub()
        ArticleRelatedStub()
    }
}

@Composable
private fun ArticleAuthorCard(article: ArticleContent) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(0.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = article.author?.displayName?.firstOrNull()?.uppercaseChar()?.toString() ?: "H",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = article.author?.displayName ?: "Habr RSS",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Автор публикации",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = {}, enabled = false) {
                Text("Подписаться")
            }
        }
    }
}

@Composable
private fun ArticleCommentsStub() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(0.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text("Комментарии", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                text = "Комментарии в RSS недоступны. Откройте оригинал, чтобы читать обсуждение на Хабре.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ArticleRelatedStub() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(0.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Публикации", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                text = "Лучшие за сутки и похожие материалы будут доступны после локального индекса и истории чтения.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ArticleToolbar(article: ArticleContent) {
    val actions = rememberArticleActions()
    val validUrl = article.url.normalizedExternalUrl()
    val canOpen = validUrl != null
    var statusMessage by remember(article.id) { mutableStateOf<String?>(null) }
    
    // Auto-dismiss status message after 3 seconds
    LaunchedEffect(statusMessage) {
        if (statusMessage != null) {
            delay(3000)
            statusMessage = null
        }
    }
    
    fun openOriginal() {
        statusMessage = if (validUrl != null && actions.openUrl(validUrl)) {
            "Открываю оригинал"
        } else {
            "Не удалось открыть ссылку"
        }
    }

    fun copyShareText() {
        statusMessage = if (actions.shareText(article.shareText())) {
            "Поделиться: готово"
        } else {
            "Не удалось поделиться"
        }
    }

    fun copyMarkdown() {
        statusMessage = if (actions.copyText(article.markdownText())) {
            "Markdown скопирован"
        } else {
            "Не удалось скопировать Markdown"
        }
    }

    Column(
        modifier = Modifier.widthIn(max = 860.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (canOpen) {
                OutlinedButton(onClick = { openOriginal() }) { Text("Оригинал") }
                OutlinedButton(onClick = { copyShareText() }) { Text("Поделиться") }
            } else {
                OutlinedButton(onClick = { openOriginal() }, enabled = false) { Text("Оригинал") }
                OutlinedButton(onClick = { copyShareText() }) { Text("Поделиться") }
            }
            OutlinedButton(onClick = { copyMarkdown() }) { Text("Markdown") }
        }
        statusMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ArticleBlockView(
    block: ArticleBlock,
    settings: FeedSettings,
    modifier: Modifier,
) {
    when (block) {
        is ArticleBlock.CodeBlock -> CodeBlockView(
            language = block.language,
            code = block.code,
            modifier = modifier,
        )
        is ArticleBlock.Heading -> InlineText(
            inline = block.inline,
            modifier = modifier,
            style = (if (block.level <= 2) {
                MaterialTheme.typography.titleLarge
            } else {
                MaterialTheme.typography.titleMedium
            }).copy(
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            ),
        )
        is ArticleBlock.Image -> ArticleContentImage(
            imageUrl = block.url,
            contentDescription = block.alt ?: "Изображение",
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = 140.dp, max = 360.dp),
        )
        is ArticleBlock.ListBlock -> ListBlockView(
            block = block,
            settings = settings,
            modifier = modifier,
        )
        is ArticleBlock.Paragraph -> InlineText(
            inline = block.inline,
            modifier = modifier,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = MaterialTheme.typography.bodyLarge.fontSize * settings.fontScale,
                lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * settings.lineHeightScale,
                color = MaterialTheme.colorScheme.onSurface,
            ),
        )
        is ArticleBlock.Quote -> QuoteBlockView(
            block = block,
            settings = settings,
            modifier = modifier,
        )
        is ArticleBlock.TableBlock -> TableBlockView(block, modifier)
        is ArticleBlock.UnknownHtml -> Text(
            text = block.html,
            modifier = modifier,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun InlineText(
    inline: List<InlineNode>,
    modifier: Modifier,
    style: TextStyle,
) {
    val annotatedString = remember(inline) { inline.toAnnotatedString() }
    val actions = rememberArticleActions()
    ClickableText(
        text = annotatedString,
        modifier = modifier,
        style = style,
        onClick = { offset ->
            annotatedString
                .getStringAnnotations(tag = LINK_TAG, start = offset, end = offset)
                .firstOrNull()
                ?.let { annotation ->
                    actions.openUrl(annotation.item)
                }
        },
    )
}

@Composable
private fun ListBlockView(
    block: ArticleBlock.ListBlock,
    settings: FeedSettings,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        block.items.forEachIndexed { index, item ->
            Row(
                modifier = modifier,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = if (block.ordered) "${index + 1}." else "•",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item.forEach { child ->
                        ArticleBlockView(
                            block = child,
                            settings = settings,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuoteBlockView(
    block: ArticleBlock.Quote,
    settings: FeedSettings,
    modifier: Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            block.blocks.forEach { child ->
                ArticleBlockView(
                    block = child,
                    settings = settings,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun CodeBlockView(
    language: String?,
    code: String,
    modifier: Modifier,
) {
    val actions = rememberArticleActions()
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(8.dp),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = language ?: "code",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = { actions.copyText(code) }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Копировать")
                }
            }
            Box(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            ) {
                Text(
                    text = code,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun TableBlockView(
    block: ArticleBlock.TableBlock,
    modifier: Modifier,
) {
    Box(modifier.horizontalScroll(rememberScrollState())) {
        Column {
            block.rows.forEach { row ->
                Row {
                    row.forEach { cell ->
                        Surface(
                            modifier = Modifier.widthIn(min = 140.dp, max = 260.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            color = MaterialTheme.colorScheme.surface,
                        ) {
                            Column(Modifier.padding(8.dp)) {
                                cell.forEach { child ->
                                    Text(
                                        text = child.blockText(),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HubArticleChip(
    hub: Hub,
    onClick: () -> Unit,
) {
    AssistChip(
        onClick = onClick,
        label = { Text(hub.title, maxLines = 1) },
    )
}

@Composable
private fun TagArticleChip(
    tag: Tag,
    favorite: Boolean,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
) {
    AssistChip(
        onClick = onClick,
        label = { Text("#${tag.title}", maxLines = 1) },
        trailingIcon = {
            IconButton(
                onClick = onFavoriteClick,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = if (favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = if (favorite) "Убрать тег из избранного" else "Добавить тег в избранное",
                    modifier = Modifier.size(18.dp),
                )
            }
        },
    )
}

internal fun List<InlineNode>.toAnnotatedString(): AnnotatedString = buildAnnotatedString {
    forEach { node ->
        appendInlineNode(node)
    }
}

private fun AnnotatedString.Builder.appendInlineNode(node: InlineNode) {
    when (node) {
        is InlineNode.Text -> append(node.value)
        is InlineNode.Code -> withStyle(
            SpanStyle(
                fontFamily = FontFamily.Monospace,
                background = Color.Unspecified, // Will be styled by SelectionContainer
            )
        ) {
            append(node.value)
        }
        is InlineNode.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            node.children.forEach { appendInlineNode(it) }
        }
        is InlineNode.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
            node.children.forEach { appendInlineNode(it) }
        }
        is InlineNode.Link -> {
            pushStringAnnotation(tag = LINK_TAG, annotation = node.url.normalizedExternalUrl() ?: node.url)
            withStyle(
                SpanStyle(
                    color = Color.Unspecified, // Color handled by MaterialTheme.colorScheme.primary in TextStyle
                    textDecoration = TextDecoration.Underline,
                )
            ) {
                append(node.text)
            }
            pop()
        }
    }
}

private fun ArticleBlock.blockText(): String = when (this) {
    is ArticleBlock.CodeBlock -> code
    is ArticleBlock.Heading -> inline.plainText()
    is ArticleBlock.Image -> alt ?: url
    is ArticleBlock.ListBlock -> items.flatten().joinToString(" ") { it.blockText() }
    is ArticleBlock.Paragraph -> inline.plainText()
    is ArticleBlock.Quote -> blocks.joinToString(" ") { it.blockText() }
    is ArticleBlock.TableBlock -> rows.flatten().flatten().joinToString(" ") { it.blockText() }
    is ArticleBlock.UnknownHtml -> html
}

internal fun List<InlineNode>.plainText(): String = joinToString("") { node ->
    when (node) {
        is InlineNode.Bold -> node.children.plainText()
        is InlineNode.Code -> node.value
        is InlineNode.Italic -> node.children.plainText()
        is InlineNode.Link -> node.text
        is InlineNode.Text -> node.value
    }
}

internal fun ArticleContent.shareText(): String = buildString {
    append(title)
    url.normalizedExternalUrl()?.let {
        appendLine()
        append(it)
    }
}

internal fun ArticleContent.markdownText(): String = MarkdownExporter().export(this)

internal fun String.normalizedExternalUrl(): String? {
    val normalized = trim().replace("&amp;", "&")
    return normalized.takeIf { it.startsWith("http://") || it.startsWith("https://") }
}
