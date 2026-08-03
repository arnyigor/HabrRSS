package com.arny.habrrss.data.remote.habr.mapper

import com.arny.habrrss.data.remote.habr.dto.HabrArticleDto
import com.arny.habrrss.data.remote.habr.dto.HabrArticlesPageDto
import com.arny.habrrss.data.rss.HtmlArticleParser
import com.arny.habrrss.domain.models.ArticleBlock
import com.arny.habrrss.domain.models.ArticleContent
import com.arny.habrrss.domain.models.Author
import com.arny.habrrss.domain.models.FeedItem
import com.arny.habrrss.domain.models.Hub
import com.arny.habrrss.domain.models.InlineNode
import com.arny.habrrss.domain.models.Tag
import com.arny.habrrss.domain.util.toEpochMillis
import com.fleeksoft.ksoup.Ksoup

class HabrArticleMapper {
    fun orderedArticles(page: HabrArticlesPageDto): List<HabrArticleDto> =
        page.publicationIds.mapNotNull(page.publicationRefs::get)

    fun missingReferenceIds(page: HabrArticlesPageDto): List<String> =
        page.publicationIds.filterNot(page.publicationRefs::containsKey)

    fun toFeedItem(dto: HabrArticleDto, feedId: String): FeedItem {
        val articleId = dto.id.toHabrArticleId()
        val url = dto.id.toHabrArticleUrl()
        val previewHtml = dto.leadData?.textHtml
        val summary = previewHtml.toPlainText()
        val title = dto.titleHtml.toPlainText().ifBlank { "Без заголовка" }

        return FeedItem(
            id = articleId,
            feedId = feedId,
            title = title,
            summary = summary,
            descriptionHtml = previewHtml,
            url = url,
            imageUrl = dto.leadData?.imageUrl ?: dto.leadData?.image?.url,
            author = dto.author?.toDomain(),
            publishedAt = dto.timePublished,
            publishedAtEpoch = dto.timePublished?.toEpochMillis(),
            tags = emptyList(),
            hubs = dto.hubs.mapNotNull { hub ->
                val titleValue = hub.title ?: hub.titleHtml.toPlainText()
                val id = hub.alias ?: hub.id ?: titleValue.stableMetadataId(prefix = "hub")
                if (id.isNullOrBlank() || titleValue.isNullOrBlank()) {
                    null
                } else {
                    Hub(id = id, title = titleValue, slug = hub.alias)
                }
            },
            rating = dto.statistics?.score?.toString(),
            commentsCount = dto.statistics?.commentsCount?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt(),
            isRead = false,
            isBookmarked = false,
        )
    }

    fun toArticleContent(dto: HabrArticleDto, articleUrl: String = dto.id.toHabrArticleUrl()): ArticleContent {
        val html = dto.textHtml ?: dto.leadData?.textHtml.orEmpty()
        val blocks = HtmlArticleParser.parse(html, articleUrl).ifEmpty {
            listOf(ArticleBlock.Paragraph(listOf(InlineNode.Text(html.toPlainText()))))
        }
        val title = dto.titleHtml.toPlainText().ifBlank { "Без заголовка" }

        return ArticleContent(
            id = dto.id.toHabrArticleId(),
            title = title,
            url = articleUrl,
            imageUrl = dto.leadData?.imageUrl ?: dto.leadData?.image?.url,
            author = dto.author?.toDomain(),
            publishedAt = dto.timePublished,
            tags = emptyList(),
            hubs = dto.hubs.mapNotNull { hub ->
                val titleValue = hub.title ?: hub.titleHtml.toPlainText()
                val id = hub.alias ?: hub.id ?: titleValue.stableMetadataId(prefix = "hub")
                if (id.isNullOrBlank() || titleValue.isNullOrBlank()) {
                    null
                } else {
                    Hub(id = id, title = titleValue, slug = hub.alias)
                }
            },
            blocks = blocks,
            sourceNotice = if (dto.textHtml.isNullOrBlank()) {
                "Контент получен из превью Habr API. Для полной версии откройте оригинал."
            } else {
                "Полная статья загружена с Habr."
            },
        )
    }

    private fun com.arny.habrrss.data.remote.habr.dto.HabrAuthorDto.toDomain(): Author {
        val displayName = fullname?.takeIf(String::isNotBlank)
            ?: alias?.takeIf(String::isNotBlank)
            ?: "Аноним"
        return Author(
            id = id ?: alias?.let { "author-$it" } ?: "author-${displayName.hashCode()}",
            displayName = displayName,
            profileUrl = alias?.let { "https://habr.com/ru/users/$it/" },
        )
    }

    private fun String?.toPlainText(): String =
        this?.let { Ksoup.parseBodyFragment(it).text().trim() }.orEmpty()

    private fun String.toHabrArticleId(): String = if (startsWith("habr-")) this else "habr-$this"

    private fun String.toHabrArticleUrl(): String =
        "https://habr.com/ru/articles/${removePrefix("habr-")}/"

    private fun String.stableMetadataId(prefix: String): String {
        val normalized = trim().replace(Regex("\\s+"), " ").lowercase()
        return "$prefix-${normalized.hashCode()}"
    }
}
