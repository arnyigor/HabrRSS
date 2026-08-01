package com.arny.habrrss.data.repository

import com.arny.habrrss.data.database.ArticleLocalStateEntity
import com.arny.habrrss.data.database.FavoriteArticleEntity
import com.arny.habrrss.data.database.FavoriteHubEntity
import com.arny.habrrss.data.database.FavoriteTagEntity
import com.arny.habrrss.data.database.FeedDao
import com.arny.habrrss.data.database.FeedItemEntity
import com.arny.habrrss.data.preferences.CustomFeedPreference
import com.arny.habrrss.data.preferences.UserPreferencesRepository
import com.arny.habrrss.data.rss.GenericRssSource
import com.arny.habrrss.data.rss.HtmlArticleParser
import com.arny.habrrss.domain.models.ArticleBlock
import com.arny.habrrss.domain.models.ArticleContent
import com.arny.habrrss.domain.models.Author
import com.arny.habrrss.domain.models.FeedDescriptor
import com.arny.habrrss.domain.models.FeedItem
import com.arny.habrrss.domain.models.FeedKind
import com.arny.habrrss.domain.models.FeedPage
import com.arny.habrrss.domain.models.InlineNode
import com.arny.habrrss.domain.models.PageCursor
import com.arny.habrrss.domain.source.ArticleContentSource
import com.arny.habrrss.domain.source.FeedSource
import com.arny.habrrss.domain.source.SourceUnavailableException
import kotlinx.coroutines.CancellationException
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json

class TechReaderRepository(
    private val primarySource: FeedSource,
    private val feedDao: FeedDao,
    private val articleContentSource: ArticleContentSource? = null,
    private val secondarySources: List<FeedSource> = emptyList(),
    private val customRssSource: GenericRssSource? = null,
    private val preferencesRepository: UserPreferencesRepository? = null,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private var cachedFeeds: List<FeedDescriptor> = emptyList()
    private var sourceByFeedId: Map<String, FeedSource> = emptyMap()
    private val externalArticles = mutableMapOf<String, ArticleContent>()
    // Track next cursor per feed for pagination
    private val feedCursorsFlow = MutableStateFlow<Map<String, PageCursor?>>(emptyMap())

    suspend fun getFeeds(forceRefresh: Boolean = false): List<FeedDescriptor> {
        if (cachedFeeds.isEmpty() || forceRefresh) {
            val sources = listOf(primarySource) + secondarySources
            val fixedFeeds = sources.flatMap { it.getFeeds() }
            val customFeeds = preferencesRepository?.customFeeds()?.first().orEmpty().map { it.toDescriptor() }
            customRssSource?.setFeeds(customFeeds)
            cachedFeeds = fixedFeeds + customFeeds
            sourceByFeedId = buildMap {
                sources.forEach { source -> source.getFeeds().forEach { put(it.id, source) } }
                customFeeds.forEach { feed -> customRssSource?.let { put(feed.id, it) } }
            }
        }
        return cachedFeeds
    }

    suspend fun refreshFeed(feedId: String): FeedPage {
        // Reset cursor for fresh load
        feedCursorsFlow.update { it + (feedId to null) }

        val page = sourceFor(feedId).getItems(feedId, page = null)
        val items = page.items.applyPersistedLocalState()
        val entities = page.items.map { item ->
            item.toEntity(
                json = json,
                cachedArticleJson = feedDao.getById(item.id)?.cachedArticleJson,
            )
        }
        feedDao.insertAll(entities)

        // Store cursor for next page
        feedCursorsFlow.update { it + (feedId to page.nextCursor) }

        return FeedPage(
            items = items,
            nextCursor = page.nextCursor,
            fromCache = false,
            updatedAt = page.updatedAt,
        )
    }

    /**
     * Load next page of feed items.
     * Returns null if there's no more pages (cursor is null).
     */
    suspend fun loadNextPage(feedId: String): FeedPage? {
        val cursor = feedCursorsFlow.value[feedId] ?: return null

        val page = sourceFor(feedId).getItems(feedId, page = cursor)
        val items = page.items.applyPersistedLocalState()
        val entities = page.items.map { item ->
            item.toEntity(
                json = json,
                cachedArticleJson = feedDao.getById(item.id)?.cachedArticleJson,
            )
        }
        feedDao.insertAll(entities)

        // Update cursor for next page
        feedCursorsFlow.update { it + (feedId to page.nextCursor) }

        return FeedPage(
            items = items,
            nextCursor = page.nextCursor,
            fromCache = false,
            updatedAt = page.updatedAt,
        )
    }

    /**
     * Check if more pages are available for a feed
     */
    fun hasMorePages(feedId: String): Boolean = feedCursorsFlow.value[feedId] != null

    fun observeFeed(feedId: String): Flow<List<FeedItem>> = combine(
        feedDao.getByFeed(feedId),
        feedDao.getArticleLocalStates(),
        feedDao.getFavoriteArticles(),
    ) { entities, localStates, favorites ->
        entities.map { entity -> entity.toDomain(json, localStates.byArticleId(), favorites.articleIds()) }
    }

    fun observeBookmarks(): Flow<List<FeedItem>> = combine(
        feedDao.getBookmarks(),
        feedDao.getArticleLocalStates(),
        feedDao.getFavoriteArticles(),
    ) { entities, localStates, favorites ->
        entities.map { entity -> entity.toDomain(json, localStates.byArticleId(), favorites.articleIds()) }
    }

    fun observeArticleItem(articleId: String): Flow<FeedItem?> = combine(
        feedDao.observeById(articleId),
        feedDao.getArticleLocalStates(),
        feedDao.getFavoriteArticles(),
    ) { entity, localStates, favorites ->
        entity?.toDomain(json, localStates.byArticleId(), favorites.articleIds())
    }

    suspend fun getCachedFeed(feedId: String): List<FeedItem> {
        val localStates = feedDao.getArticleLocalStatesOnce().byArticleId()
        val favorites = feedDao.getFavoriteArticlesOnce().articleIds()
        return feedDao.getByFeedOnce(feedId).map { it.toDomain(json, localStates, favorites) }
    }

    suspend fun isBookmarked(articleId: String): Boolean =
        feedDao.getFavoriteArticlesOnce().any { it.articleId == articleId }

    suspend fun getArticle(articleId: String): ArticleContent {
        markRead(articleId)

        val entity = feedDao.getById(articleId)
            ?: throw SourceUnavailableException("Article not found: $articleId")

        val cachedArticle = entity.cachedArticleJson?.let { cached ->
            runCatching { json.decodeFromString<ArticleContent>(cached) }.getOrNull()
        }
        val fallback = cachedArticle ?: buildArticleContent(entity)

        // Try to fetch full article using dedicated content source
        val fullArticle = articleContentSource?.let { source ->
            try {
                source.getArticleByUrl(entity.url)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
        }

        return when {
            fullArticle != null && fullArticle.blocks.isNotEmpty() -> {
                // Merge: preserve author from RSS fallback if full article doesn't have it
                val merged = fallback.copy(
                    imageUrl = fallback.imageUrl ?: fullArticle.imageUrl,
                    blocks = fullArticle.blocks,
                    sourceNotice = fullArticle.sourceNotice,
                    author = fallback.author ?: fullArticle.author,
                )
                feedDao.update(entity.copy(cachedArticleJson = json.encodeToString(merged)))
                merged
            }
            else -> fallback
        }
    }

    suspend fun getArticleByUrl(url: String): ArticleContent {
        val existing = feedDao.getByUrl(url) ?: feedDao.getByUrl(url.trimEnd('/'))
        if (existing != null) return getArticle(existing.id)
        val source = articleContentSource ?: throw SourceUnavailableException("Article source is not available.")
        return source.getArticleByUrl(url).also { article ->
            externalArticles[article.id] = article
        }
    }

    suspend fun toggleBookmark(articleId: String) {
        val entity = feedDao.getById(articleId)
        if (entity == null) {
            val article = externalArticles[articleId] ?: return
            feedDao.insertAll(listOf(article.toExternalEntity(json)))
        }
        setBookmarked(articleId, !isBookmarked(articleId))
    }

    suspend fun getBookmarks(): List<FeedItem> {
        val localStates = feedDao.getArticleLocalStatesOnce().byArticleId()
        val favorites = feedDao.getFavoriteArticlesOnce().articleIds()
        return feedDao.getBookmarksOnce().map { it.toDomain(json, localStates, favorites) }
    }

    suspend fun search(query: String): List<FeedItem> {
        if (query.isBlank()) return emptyList()
        val localStates = feedDao.getArticleLocalStatesOnce().byArticleId()
        val favorites = feedDao.getFavoriteArticlesOnce().articleIds()
        return feedDao.search(query).map { it.toDomain(json, localStates, favorites) }
    }

    suspend fun upsertCustomFeed(id: String?, title: String, url: String) {
        val feed = CustomFeedPreference(
            id = id ?: "custom-${url.normalizedFeedUrl().hashCode()}",
            title = title.ifBlank { url },
            url = url.normalizedFeedUrl(),
        )
        preferencesRepository?.upsertCustomFeed(feed)
        cachedFeeds = emptyList()
        getFeeds(forceRefresh = true)
    }

    suspend fun removeCustomFeed(id: String) {
        preferencesRepository?.removeCustomFeed(id)
        cachedFeeds = emptyList()
        getFeeds(forceRefresh = true)
    }

    fun observeFavoriteTagIds(): Flow<Set<String>> =
        feedDao.getFavoriteTags().map { tags -> tags.mapTo(mutableSetOf()) { it.tagId } }

    fun observeFavoriteHubIds(): Flow<Set<String>> =
        feedDao.getFavoriteHubs().map { hubs -> hubs.mapTo(mutableSetOf()) { it.hubId } }

    suspend fun getFavoriteTagIds(): Set<String> =
        feedDao.getFavoriteTagsOnce().mapTo(mutableSetOf()) { it.tagId }

    suspend fun getFavoriteHubIds(): Set<String> =
        feedDao.getFavoriteHubsOnce().mapTo(mutableSetOf()) { it.hubId }

    suspend fun toggleFavoriteTag(tagId: String, title: String? = null) {
        if (feedDao.getFavoriteTagsOnce().any { it.tagId == tagId }) {
            feedDao.deleteFavoriteTag(tagId)
        } else {
            feedDao.insertFavoriteTag(
                FavoriteTagEntity(
                    tagId = tagId,
                    title = title,
                    createdAt = Clock.System.now().toEpochMilliseconds(),
                )
            )
        }
    }

    suspend fun toggleFavoriteHub(hubId: String, title: String? = null) {
        if (feedDao.getFavoriteHubsOnce().any { it.hubId == hubId }) {
            feedDao.deleteFavoriteHub(hubId)
        } else {
            feedDao.insertFavoriteHub(
                FavoriteHubEntity(
                    hubId = hubId,
                    title = title,
                    createdAt = Clock.System.now().toEpochMilliseconds(),
                )
            )
        }
    }

    fun requireFirstFeedId(): String {
        return cachedFeeds.firstOrNull()?.id
            ?: throw SourceUnavailableException("No feed descriptors are available.")
    }

    private suspend fun markRead(articleId: String) {
        val current = feedDao.getArticleLocalState(articleId)
        feedDao.upsertArticleLocalState(
            (current ?: ArticleLocalStateEntity(articleId = articleId)).copy(
                isRead = true,
                lastOpenedAt = Clock.System.now().toEpochMilliseconds(),
            )
        )
    }

    private suspend fun setBookmarked(articleId: String, isBookmarked: Boolean) {
        if (isBookmarked) {
            feedDao.insertFavoriteArticle(
                FavoriteArticleEntity(
                    articleId = articleId,
                    createdAt = Clock.System.now().toEpochMilliseconds(),
                )
            )
        } else {
            feedDao.deleteFavoriteArticle(articleId)
        }
    }

    private suspend fun sourceFor(feedId: String): FeedSource {
        if (sourceByFeedId.isEmpty()) getFeeds(forceRefresh = true)
        return sourceByFeedId[feedId] ?: primarySource
    }

    private suspend fun List<FeedItem>.applyPersistedLocalState(): List<FeedItem> {
        val localStates = feedDao.getArticleLocalStatesOnce().byArticleId()
        val favorites = feedDao.getFavoriteArticlesOnce().articleIds()
        return map { item -> item.withLocalState(localStates, favorites) }
    }

    private fun CustomFeedPreference.toDescriptor(): FeedDescriptor = FeedDescriptor(
        id = id,
        title = title,
        sourceTitle = "Custom RSS",
        url = url,
        description = "Пользовательская RSS-лента",
        kind = FeedKind.Custom,
    )

    private fun String.normalizedFeedUrl(): String = trim().replace("&amp;", "&")

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

private fun ArticleContent.toExternalEntity(json: Json): FeedItemEntity = FeedItemEntity(
    id = id,
    feedId = "external",
    title = title,
    summary = blocks.joinToString(" ") { it.toPlainText() }.take(500),
    descriptionHtml = null,
    url = url,
    imageUrl = imageUrl,
    authorName = author?.displayName,
    authorProfileUrl = author?.profileUrl,
    publishedAt = publishedAt,
    publishedAtEpoch = null,
    tagsJson = json.encodeToString(tags),
    hubsJson = json.encodeToString(hubs),
    rating = null,
    commentsCount = null,
    cachedArticleJson = json.encodeToString(this),
    fetchedAt = Clock.System.now().toEpochMilliseconds(),
)

private fun ArticleBlock.toPlainText(): String = when (this) {
    is ArticleBlock.CodeBlock -> code
    is ArticleBlock.Heading -> inline.joinToString("") { it.toPlainText() }
    is ArticleBlock.Image -> alt.orEmpty()
    is ArticleBlock.ListBlock -> items.flatten().joinToString(" ") { it.toPlainText() }
    is ArticleBlock.Paragraph -> inline.joinToString("") { it.toPlainText() }
    is ArticleBlock.Quote -> blocks.joinToString(" ") { it.toPlainText() }
    is ArticleBlock.Spoiler -> blocks.joinToString(" ") { it.toPlainText() }
    is ArticleBlock.TableBlock -> rows.flatten().flatten().joinToString(" ") { it.toPlainText() }
    is ArticleBlock.UnknownHtml -> html
}

private fun InlineNode.toPlainText(): String = when (this) {
    is InlineNode.Bold -> children.joinToString("") { it.toPlainText() }
    is InlineNode.Code -> value
    is InlineNode.Italic -> children.joinToString("") { it.toPlainText() }
    is InlineNode.Link -> text
    is InlineNode.Text -> value
}

private fun FeedItem.toEntity(
    json: Json,
    cachedArticleJson: String? = null,
): FeedItemEntity = FeedItemEntity(
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
    publishedAtEpoch = publishedAtEpoch,
    tagsJson = json.encodeToString(tags),
    hubsJson = json.encodeToString(hubs),
    rating = rating,
    commentsCount = commentsCount,
    cachedArticleJson = cachedArticleJson,
    fetchedAt = Clock.System.now().toEpochMilliseconds(),
)

private fun FeedItemEntity.toDomain(
    json: Json,
    localStates: Map<String, ArticleLocalStateEntity> = emptyMap(),
    favoriteArticleIds: Set<String> = emptySet(),
): FeedItem = FeedItem(
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
    publishedAtEpoch = publishedAtEpoch,
    tags = json.decodeFromString(tagsJson),
    hubs = json.decodeFromString(hubsJson),
    rating = rating,
    commentsCount = commentsCount,
    isRead = localStates[id]?.isRead == true,
    isBookmarked = id in favoriteArticleIds,
)

private fun FeedItem.withLocalState(
    localStates: Map<String, ArticleLocalStateEntity>,
    favoriteArticleIds: Set<String>,
): FeedItem = copy(
    isRead = localStates[id]?.isRead == true,
    isBookmarked = id in favoriteArticleIds,
)

private fun List<ArticleLocalStateEntity>.byArticleId(): Map<String, ArticleLocalStateEntity> =
    associateBy { it.articleId }

private fun List<FavoriteArticleEntity>.articleIds(): Set<String> =
    mapTo(mutableSetOf()) { it.articleId }
