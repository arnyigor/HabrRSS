package com.arny.habrrss.ui.article

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arny.habrrss.domain.models.ArticleBlock
import com.arny.habrrss.domain.models.FeedSettings
import com.arny.habrrss.domain.models.Hub
import com.arny.habrrss.domain.models.InlineNode
import com.arny.habrrss.domain.models.Tag

@Composable
internal fun ArticleBlockView(
    block: ArticleBlock,
    settings: FeedSettings,
    modifier: Modifier,
    onLinkClick: ((String) -> Unit)? = null,
    highlightQuery: String? = null,
    highlightCurrentRange: IntRange? = null,
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
            onLinkClick = onLinkClick,
            highlightQuery = highlightQuery,
            highlightCurrentRange = highlightCurrentRange,
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
            onLinkClick = onLinkClick,
            highlightQuery = highlightQuery,
            highlightCurrentRange = highlightCurrentRange,
        )
        is ArticleBlock.Paragraph -> InlineText(
            inline = block.inline,
            modifier = modifier,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = MaterialTheme.typography.bodyLarge.fontSize * settings.fontScale,
                lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * settings.lineHeightScale,
                color = MaterialTheme.colorScheme.onSurface,
            ),
            onLinkClick = onLinkClick,
            highlightQuery = highlightQuery,
            highlightCurrentRange = highlightCurrentRange,
        )
        is ArticleBlock.Quote -> QuoteBlockView(
            block = block,
            settings = settings,
            modifier = modifier,
            onLinkClick = onLinkClick,
            highlightQuery = highlightQuery,
            highlightCurrentRange = highlightCurrentRange,
        )
        is ArticleBlock.Spoiler -> SpoilerBlockView(
            block = block,
            settings = settings,
            modifier = modifier,
            onLinkClick = onLinkClick,
            highlightQuery = highlightQuery,
            highlightCurrentRange = highlightCurrentRange,
        )
        is ArticleBlock.TableBlock -> TableBlockView(
            block = block,
            modifier = modifier,
            onLinkClick = onLinkClick,
            highlightQuery = highlightQuery,
            highlightCurrentRange = highlightCurrentRange,
        )
        is ArticleBlock.UnknownHtml -> Text(
            text = block.html,
            modifier = modifier,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun SpoilerBlockView(
    block: ArticleBlock.Spoiler,
    settings: FeedSettings,
    modifier: Modifier,
    onLinkClick: ((String) -> Unit)? = null,
    highlightQuery: String? = null,
    highlightCurrentRange: IntRange? = null,
) {
    var isExpanded by remember(block.title) { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = block.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 3,
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (isExpanded) "Свернуть спойлер" else "Раскрыть спойлер",
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    block.blocks.forEach { childBlock ->
                        ArticleBlockView(
                            block = childBlock,
                            settings = settings,
                            modifier = Modifier.fillMaxWidth(),
                            onLinkClick = onLinkClick,
                            highlightQuery = highlightQuery,
                            highlightCurrentRange = highlightCurrentRange,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InlineText(
    inline: List<InlineNode>,
    modifier: Modifier,
    style: TextStyle,
    onLinkClick: ((String) -> Unit)? = null,
    highlightQuery: String? = null,
    highlightCurrentRange: IntRange? = null,
) {
    val base = remember(inline) { inline.toAnnotatedString() }
    val annotatedString = remember(base, highlightQuery, highlightCurrentRange) {
        base.withSearchHighlight(highlightQuery ?: "", highlightCurrentRange)
    }
    if (onLinkClick != null) {
        // Clickable text with link handling using clickable + pointerInput
        val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        Box(
            modifier = modifier
                .clickable(interactionSource = interactionSource, indication = null) {
                    // Click handled by pointerInput below
                }
                .pointerInput(annotatedString) {
                    detectTapGestures { offset: Offset ->
                        val start = offset.x.toInt().coerceIn(0, annotatedString.length - 1)
                        val end = (offset.x.toInt() + 1).coerceIn(0, annotatedString.length)
                        val annotation = annotatedString.getStringAnnotations(
                            tag = LINK_TAG,
                            start = start,
                            end = end
                        ).firstOrNull()
                        annotation?.item?.let { url ->
                            onLinkClick(url)
                        }
                    }
                }
        ) {
            SelectionContainer {
                Text(
                    text = annotatedString,
                    style = style,
                )
            }
        }
    } else {
        SelectionContainer {
            Text(
                text = annotatedString,
                modifier = modifier,
                style = style,
            )
        }
    }
}

@Composable
private fun ListBlockView(
    block: ArticleBlock.ListBlock,
    settings: FeedSettings,
    modifier: Modifier,
    onLinkClick: ((String) -> Unit)? = null,
    highlightQuery: String? = null,
    highlightCurrentRange: IntRange? = null,
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
                            onLinkClick = onLinkClick,
                            highlightQuery = highlightQuery,
                            highlightCurrentRange = highlightCurrentRange,
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
    onLinkClick: ((String) -> Unit)? = null,
    highlightQuery: String? = null,
    highlightCurrentRange: IntRange? = null,
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
                    onLinkClick = onLinkClick,
                    highlightQuery = highlightQuery,
                    highlightCurrentRange = highlightCurrentRange,
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
                SelectionContainer {
                    Text(
                        text = remember(code, language) { code.toHighlightedCode(language) },
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

@Composable
private fun TableBlockView(
    block: ArticleBlock.TableBlock,
    modifier: Modifier,
    onLinkClick: ((String) -> Unit)? = null,
    highlightQuery: String? = null,
    highlightCurrentRange: IntRange? = null,
) {
    if (block.rows.isEmpty()) return
    val columnCount = block.rows.maxOfOrNull { it.size } ?: 0
    if (columnCount == 0) return
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val availableCellWidth = maxWidth / columnCount.toFloat()
        val cellWidth = if (columnCount <= 2) {
            availableCellWidth.coerceAtLeast(160.dp)
        } else {
            availableCellWidth.coerceIn(160.dp, 280.dp)
        }

        Box(Modifier.horizontalScroll(rememberScrollState())) {
            Column {
                block.rows.forEachIndexed { rowIndex, row ->
                    Row {
                        val isFirstRow = rowIndex == 0
                        repeat(columnCount) { columnIndex ->
                            TableCellContent(
                                cell = row.getOrNull(columnIndex).orEmpty(),
                                isHeader = isFirstRow,
                                onLinkClick = onLinkClick,
                                highlightQuery = highlightQuery,
                                highlightCurrentRange = highlightCurrentRange,
                                modifier = Modifier.width(cellWidth),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TableCellContent(
    cell: List<ArticleBlock>,
    isHeader: Boolean,
    onLinkClick: ((String) -> Unit)?,
    highlightQuery: String? = null,
    highlightCurrentRange: IntRange? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = if (isHeader) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(8.dp)) {
            cell.forEach { child ->
                ArticleBlockView(
                    block = child,
                    settings = FeedSettings.defaults(),
                    modifier = Modifier,
                    onLinkClick = onLinkClick,
                    highlightQuery = highlightQuery,
                    highlightCurrentRange = highlightCurrentRange,
                )
            }
        }
    }
}

@Composable
internal fun HubArticleChip(
    hub: Hub,
    favorite: Boolean,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
) {
    AssistChip(
        onClick = onClick,
        modifier = Modifier.widthIn(max = 220.dp),
        label = { Text(hub.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingIcon = {
            IconButton(
                onClick = onFavoriteClick,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = if (favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = if (favorite) "Убрать хаб из избранного" else "Добавить хаб в избранное",
                    tint = if (favorite) Color(0xFFFFA000) else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(18.dp),
                )
            }
        },
    )
}

@Composable
internal fun TagArticleChip(
    tag: Tag,
    favorite: Boolean,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
) {
    AssistChip(
        onClick = onClick,
        modifier = Modifier.widthIn(max = 220.dp),
        label = { Text("#${tag.title}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingIcon = {
            IconButton(
                onClick = onFavoriteClick,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = if (favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = if (favorite) "Убрать тег из избранного" else "Добавить тег в избранное",
                    tint = if (favorite) Color(0xFFFFA000) else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(18.dp),
                )
            }
        },
    )
}
