package com.arny.habrrss.data.repository

import com.arny.habrrss.data.database.FeedDao
import com.arny.habrrss.data.database.FeedItemEntity
import com.arny.habrrss.data.rss.HtmlArticleParser
import com.arny.habrrss.domain.models.ArticleBlock
import com.arny.habrrss.domain.models.ArticleContent
import com.arny.habrrss.domain.models.Author
import com.arny.habrrss.domain.models.FeedDescriptor
import com.arny.habrrss.domain.models.FeedItem
import com.arny.habrrss.domain.models.FeedPage
import com.arny.habrrss.domain.models.InlineNode
import com.arny.habrrss.domain.source.FeedSource
import com.arny.habrrss.domain.source.SourceUnavailableException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

class TechReaderRepository(
    private val primarySource: FeedSource,
    private val feedDao: FeedDao,
    private val secondarySources: List<FeedSource> = emptyList(),
) {
    private val json = Json { ignoreUnknownKeys = true }
    private var cachedFeeds: List<FeedDescriptor> = emptyList()

    suspend fun getFeeds(): List<FeedDescriptor> {
        if (cachedFeeds.isEmpty()) {
            cachedFeeds = (listOf(primarySource) + secondarySources).flatMap { it.getFeeds() }
        }
        return cachedFeeds
    }

    suspend fun refreshFeed(feedId: String): FeedPage {
        val page = primarySource.getItems(feedId, page = null)
        val items = page.items.map { item ->
            val existing = feedDao.getById(item.id)
            item.copy(
                isRead = existing?.isRead ?: item.isRead,
                isBookmarked = existing?.isBookmarked ?: item.isBookmarked,
            )
        }
        val entities = items.map { it.toEntity(json) }
        feedDao.insertAll(entities)

        return FeedPage(
            items = items,
            nextCursor = page.nextCursor,
            fromCache = false,
            updatedAt = page.updatedAt,
        )
    }

    suspend fun getCachedFeed(feedId: String): List<FeedItem> {
        return feedDao.getByFeedOnce(feedId).map { it.toDomain(json) }
    }

    suspend fun getArticle(articleId: String): ArticleContent {
        feedDao.updateRead(articleId, true)

        val entity = feedDao.getById(articleId)
            ?: throw SourceUnavailableException("Article not found: $articleId")

        val fallback = buildArticleContent(entity)
        val fullArticle = try {
            primarySource.getArticle(entity.url)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }

        return if (fullArticle != null && fullArticle.blocks.isNotEmpty()) {
            // Preserve author from fallback (RSS) if full article doesn't have it
            fallback.copy(
                imageUrl = fallback.imageUrl ?: fullArticle.imageUrl,
                blocks = fullArticle.blocks,
                sourceNotice = fullArticle.sourceNotice,
                author = fallback.author ?: fullArticle.author,
            )
        } else {
            fallback
        }
    }

    suspend fun toggleBookmark(articleId: String) {
        val current = feedDao.getById(articleId)?.isBookmarked ?: false
        feedDao.updateBookmark(articleId, !current)
    }

    suspend fun getBookmarks(): List<FeedItem> {
        return feedDao.getBookmarksOnce().map { it.toDomain(json) }
    }

    suspend fun search(query: String): List<FeedItem> {
        if (query.isBlank()) return emptyList()
        return feedDao.search("%$query%").map { it.toDomain(json) }
    }

    fun requireFirstFeedId(): String {
        return cachedFeeds.firstOrNull()?.id
            ?: throw SourceUnavailableException("No feed descriptors are available.")
    }

    private fun buildArticleContent(entity: FeedItemEntity): ArticleContent {
        val blocks = entity.descriptionHtml?.let { HtmlArticleParser.parse(it, entity.url) }
            ?: listOf(ArticleBlock.Paragraph(listOf(InlineNode.Text(entity.summary))))

        return ArticleContent(
            id = entity.id,
            title = entity.title,
            url = entity.url,
            imageUrl = entity.imageUrl,
            author = entity.authorName?.let {
                Author(
                    id = "author-${it.hashCode()}",
                    displayName = it,
                    profileUrl = entity.authorProfileUrl
                )
            },
            publishedAt = entity.publishedAt,
            tags = json.decodeFromString(entity.tagsJson),
            hubs = json.decodeFromString(entity.hubsJson),
            blocks = blocks,
            sourceNotice = "Контент из RSS-ленты. Для полной версии откройте оригинал.",
        )
    }
}

private fun FeedItem.toEntity(json: Json): FeedItemEntity = FeedItemEntity(
    id = id,
    feedId = feedId,
    title = title,
    summary = summary,
    descriptionHtml = descriptionHtml,
    url = url,
    imageUrl = imageUrl,
    authorName = author?.displayName,
    authorProfileUrl = author?.profileUrl,
    publishedAt = publishedAt,
    tagsJson = json.encodeToString(tags),
    hubsJson = json.encodeToString(hubs),
    rating = rating,
    commentsCount = commentsCount,
    isRead = isRead,
    isBookmarked = isBookmarked,
    fetchedAt = System.currentTimeMillis(),
)

private fun FeedItemEntity.toDomain(json: Json): FeedItem = FeedItem(
    id = id,
    feedId = feedId,
    title = title,
    summary = summary,
    descriptionHtml = descriptionHtml,
    url = url,
    imageUrl = imageUrl,
    author = authorName?.let {
        Author(id = "author-${it.hashCode()}", displayName = it, profileUrl = authorProfileUrl)
    },
    publishedAt = publishedAt,
    tags = json.decodeFromString(tagsJson),
    hubs = json.decodeFromString(hubsJson),
    rating = rating,
    commentsCount = commentsCount,
    isRead = isRead,
    isBookmarked = isBookmarked,
)
