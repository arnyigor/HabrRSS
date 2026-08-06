package com.arny.habrrss.data.repository

import com.arny.habrrss.data.database.ArticleLocalStateEntity
import com.arny.habrrss.data.database.FavoriteArticleEntity
import com.arny.habrrss.data.database.FavoriteHubEntity
import com.arny.habrrss.data.database.FavoriteTagEntity
import com.arny.habrrss.data.database.FeedDao
import com.arny.habrrss.data.database.FeedItemEntity
import com.arny.habrrss.data.database.SyncStateEntity
import com.arny.habrrss.data.api.HabrApiSource
import com.arny.habrrss.data.preferences.CustomFeedPreference
import com.arny.habrrss.data.preferences.UserPreferencesRepository
import com.arny.habrrss.core.logging.AppLog
import com.arny.habrrss.data.remote.habr.HabrPeriod
import com.arny.habrrss.data.remote.habr.error.HabrRemoteException
import com.arny.habrrss.data.rss.GenericRssSource
import com.arny.habrrss.data.rss.HtmlArticleParser
import com.arny.habrrss.domain.models.ArticleBlock
import com.arny.habrrss.domain.models.ArticleContent
import com.arny.habrrss.domain.models.Author
import com.arny.habrrss.domain.models.CommentNode
import com.arny.habrrss.domain.models.FeedDescriptor
import com.arny.habrrss.domain.models.FeedItem
import com.arny.habrrss.domain.models.FeedKind
import com.arny.habrrss.domain.models.FeedLoadProgress
import com.arny.habrrss.domain.models.FeedPage
import com.arny.habrrss.domain.models.InlineNode
import com.arny.habrrss.domain.models.Hub
import com.arny.habrrss.domain.models.PageCursor
import com.arny.habrrss.domain.models.Tag
import com.arny.habrrss.domain.source.ArticleCommentsSource
import com.arny.habrrss.domain.source.ArticleContentSource
import com.arny.habrrss.domain.source.FeedSource
import com.arny.habrrss.domain.source.SourceUnavailableException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
    private var fallbackSourcesByFeedId: Map<String, List<FeedSource>> = emptyMap()
    private val externalArticles = mutableMapOf<String, ArticleContent>()
    // Track next cursor per feed for pagination
    private val feedCursorsFlow = MutableStateFlow<Map<String, PageCursor?>>(emptyMap())

    suspend fun getFeeds(forceRefresh: Boolean = false): List<FeedDescriptor> {
        if (cachedFeeds.isEmpty() || forceRefresh) {
            val startedAt = Clock.System.now().toEpochMilliseconds()
            AppLog.d(TAG, "getFeeds rebuilding force=$forceRefresh")
            val sources = listOf(primarySource) + secondarySources
            val descriptorsBySource = sources.map { source -> source to source.getFeeds() }
            val fixedFeeds = descriptorsBySource.flatMap { (_, feeds) -> feeds } + allCachedDescriptor()
            val customFeeds = preferencesRepository?.customFeeds()?.first().orEmpty().map { it.toDescriptor() }
            val rssCustomFeeds = customFeeds.filterNot { it.isHabrApiFeed() }
            val apiHubRssFeeds = customFeeds.mapNotNull { it.toHabrHubRssDescriptor() }
            val customRssFeeds = rssCustomFeeds + apiHubRssFeeds
            customRssSource?.setFeeds(customRssFeeds)
            cachedFeeds = (fixedFeeds + customFeeds).distinctBy { it.id }
            sourceByFeedId = buildMap {
                descriptorsBySource.forEach { (source, feeds) ->
                    feeds.forEach { feed -> if (feed.id !in this) put(feed.id, source) }
                }
                customFeeds.forEach { feed ->
                    when {
                        feed.isHabrApiFeed() && customRssSource != null -> put(feed.id, customRssSource)
                        feed.isHabrApiFeed() -> put(feed.id, primarySource)
                        customRssSource != null -> put(feed.id, customRssSource)
                    }
                }
            }
            val apiHubFallbackFeeds = customFeeds.filter { it.isHabrApiFeed() }
            fallbackSourcesByFeedId = (
                descriptorsBySource +
                    listOfNotNull(customRssSource?.let { it to customRssFeeds }) +
                    listOf(primarySource to apiHubFallbackFeeds)
                )
                .flatMap { (source, feeds) -> feeds.map { feed -> feed.id to source } }
                .groupBy(keySelector = { it.first }, valueTransform = { it.second })
            AppLog.i(
                TAG,
                "getFeeds loaded fixed=${fixedFeeds.size} custom=${customFeeds.size} rssCustom=${rssCustomFeeds.size} " +
                    "apiHubRss=${apiHubRssFeeds.size} " +
                    "sourceMap=${sourceByFeedId.size} fallbackMap=${fallbackSourcesByFeedId.size} " +
                    "elapsed=${Clock.System.now().toEpochMilliseconds() - startedAt}ms",
            )
        } else {
            AppLog.d(TAG, "getFeeds cache hit count=${cachedFeeds.size}")
        }
        return cachedFeeds
    }

    suspend fun refreshFeed(feedId: String, force: Boolean = false): FeedPage {
        val startedAt = Clock.System.now().toEpochMilliseconds()
        AppLog.i(TAG, "refreshFeed start feedId=$feedId force=$force")
        if (feedId == HabrApiSource.FeedIds.AllCached) {
            AppLog.i(TAG, "refreshFeed local-all from cache elapsed=${Clock.System.now().toEpochMilliseconds() - startedAt}ms")
            return FeedPage(
                items = getCachedFeed(feedId),
                nextCursor = null,
                fromCache = true,
                updatedAt = null,
            )
        }

        val cached = getCachedFeed(feedId)
        val previousCursor = feedCursorsFlow.value[feedId] ?: feedDao.getSyncState(feedId)?.toPageCursor()
        if (!force && cached.isNotEmpty() && isCacheFresh(feedId)) {
            feedCursorsFlow.update { it + (feedId to previousCursor) }
            AppLog.i(
                TAG,
                "refreshFeed cache fresh feedId=$feedId cached=${cached.size} next=${previousCursor?.value} " +
                    "elapsed=${Clock.System.now().toEpochMilliseconds() - startedAt}ms",
            )
            return FeedPage(
                items = cached,
                nextCursor = previousCursor,
                fromCache = true,
                updatedAt = feedDao.getNewestFetchedAtByFeed(feedId)?.toString(),
            )
        }

        val page = try {
            loadFeedPageWithFallback(feedId, page = null)
        } catch (error: CancellationException) {
            throw error
        } catch (error: HabrRemoteException.ContractChanged) {
            return cachedFeedOrThrow(feedId, cached, previousCursor, error)
        } catch (error: HabrRemoteException.RateLimited) {
            return cachedFeedOrThrow(feedId, cached, previousCursor, error)
        } catch (error: HabrRemoteException.Server) {
            return cachedFeedOrThrow(feedId, cached, previousCursor, error)
        }
        val items = page.items.applyPersistedLocalState()
        // Hub feeds accumulate their archive across refreshes: REPLACE upsert + URL-based
        // dedup keep the local copy consistent without wiping already loaded pages.
        val entities = page.items.changedRemoteEntities()
        if (entities.isNotEmpty()) {
            feedDao.insertAll(entities)
        }
        AppLog.i(
            TAG,
            "refreshFeed remote feedId=$feedId pageItems=${page.items.size} changed=${entities.size} " +
                "fromCache=${page.fromCache} next=${page.nextCursor?.value} elapsed=${Clock.System.now().toEpochMilliseconds() - startedAt}ms",
        )

        val nextCursor = if (feedId.isHabrHubFeedId() && !page.fromCache) {
            page.nextCursor
        } else {
            maxCursor(previousCursor, page.nextCursor)
        }
        feedCursorsFlow.update { it + (feedId to nextCursor) }
        savePagingState(
            sourceKey = feedId,
            nextCursor = nextCursor,
            pagesCount = page.totalPages ?: page.nextCursor?.value?.toIntOrNull()?.minus(1),
            completed = nextCursor == null,
        )

        return FeedPage(
            items = items,
            nextCursor = nextCursor,
            fromCache = false,
            updatedAt = page.updatedAt,
            totalPages = page.totalPages,
        )
    }

    /**
     * Load next page of feed items.
     * Returns null if there's no more pages (cursor is null).
     */
    suspend fun loadNextPage(feedId: String): FeedPage? {
        val startedAt = Clock.System.now().toEpochMilliseconds()
        AppLog.i(TAG, "loadNextPage start feedId=$feedId")
        if (feedId == HabrApiSource.FeedIds.AllCached) return null
        val cursor = feedCursorsFlow.value[feedId] ?: feedDao.getSyncState(feedId)?.toPageCursor() ?: return null

        val page = try {
            loadFeedPageWithFallback(feedId, page = cursor)
        } catch (error: CancellationException) {
            throw error
        } catch (error: HabrRemoteException.ContractChanged) {
            return nullIfCachedFeedExistsOrThrow(feedId, error)
        } catch (error: HabrRemoteException.RateLimited) {
            return nullIfCachedFeedExistsOrThrow(feedId, error)
        } catch (error: HabrRemoteException.Server) {
            return nullIfCachedFeedExistsOrThrow(feedId, error)
        }
        val items = page.items.applyPersistedLocalState()
        val entities = page.items.changedRemoteEntities()
        if (entities.isNotEmpty()) {
            feedDao.insertAll(entities)
        }
        AppLog.i(
            TAG,
            "loadNextPage loaded feedId=$feedId cursor=${cursor.value} pageItems=${page.items.size} changed=${entities.size} " +
                "next=${page.nextCursor?.value} elapsed=${Clock.System.now().toEpochMilliseconds() - startedAt}ms",
        )

        // Update cursor for next page
        feedCursorsFlow.update { it + (feedId to page.nextCursor) }
        savePagingState(
            sourceKey = feedId,
            nextCursor = page.nextCursor,
            pagesCount = page.totalPages
                ?: page.nextCursor?.value?.toIntOrNull()?.minus(1)
                ?: cursor.value.toIntOrNull(),
            completed = page.nextCursor == null,
        )

        return FeedPage(
            items = items,
            nextCursor = page.nextCursor,
            fromCache = false,
            updatedAt = page.updatedAt,
            totalPages = page.totalPages,
        )
    }

    /**
     * Loads every remaining page of [feedId] one by one, continuing from the current cursor.
     *
     * The loop is cooperative: cancellation of the calling coroutine stops it at the next page
     * boundary. Already loaded pages stay in the local database.
     *
     * Unlike [loadNextPage], remote errors (rate limit, server errors, contract changes) are
     * **not** swallowed: they propagate to the caller and the paging cursor is left intact, so a
     * later retry can continue from the failed page instead of silently losing the ability to
     * load the archive at all.
     *
     * The progress reported through [onProgress] is cumulative: it starts from the pages that
     * are already persisted for this feed, so a restarted import shows e.g. "5 из 72" instead
     * of counting from zero again.
     *
     * @throws kotlinx.coroutines.CancellationException when the calling coroutine is cancelled
     * @throws Exception when a remote page fails to load (cursor is preserved)
     * @return number of pages loaded in this call
     */
    suspend fun loadAllRemainingPages(
        feedId: String,
        onProgress: (pagesProcessed: Int, totalPages: Int?) -> Unit = { _, _ -> },
    ): Int {
        if (feedId == HabrApiSource.FeedIds.AllCached) return 0
        val syncState = feedDao.getSyncState(feedId)
        val cursor = feedCursorsFlow.value[feedId] ?: syncState?.toPageCursor()
        val startingProcessed = cursor?.value?.toIntOrNull()?.minus(1)?.coerceAtLeast(0) ?: 0
        var pages = 0
        var knownTotal: Int? = syncState?.pagesCountSnapshot
        while (hasMorePages(feedId)) {
            currentCoroutineContext().ensureActive()
            val page = loadNextPageOrThrow(feedId) ?: break
            pages += 1
            knownTotal = page.totalPages ?: knownTotal
            onProgress(startingProcessed + pages, knownTotal)
        }
        return pages
    }

    /**
     * Progress of the archive import for [feedId]: pages already persisted locally and the last
     * known total page count. Survives process restarts through the persisted paging cursor and
     * the total-page snapshot, so "load all pages" can resume (and show) the real position.
     */
    suspend fun loadAllPagesProgress(feedId: String): FeedLoadProgress {
        if (feedId == HabrApiSource.FeedIds.AllCached) return FeedLoadProgress(pagesProcessed = 0, totalPages = null)
        val syncState = feedDao.getSyncState(feedId)
        val cursor = feedCursorsFlow.value[feedId] ?: syncState?.toPageCursor()
        val processed = when {
            cursor != null -> cursor.value.toIntOrNull()?.minus(1)?.coerceAtLeast(0) ?: 0
            syncState?.status == "completed" -> syncState.pagesCountSnapshot ?: syncState.pagesProcessed
            else -> 0
        }
        return FeedLoadProgress(pagesProcessed = processed, totalPages = syncState?.pagesCountSnapshot)
    }

    /**
     * Like [loadNextPage], but propagates remote errors instead of swallowing them.
     *
     * Used by [loadAllRemainingPages] so an API error (rate limit / server error / contract
     * change) does not silently destroy the paging cursor: the caller can surface the error
     * while the next page pointer stays untouched for a later retry.
     */
    private suspend fun loadNextPageOrThrow(feedId: String): FeedPage? {
        val startedAt = Clock.System.now().toEpochMilliseconds()
        val cursor = feedCursorsFlow.value[feedId] ?: feedDao.getSyncState(feedId)?.toPageCursor() ?: return null
        val page = loadFeedPageWithFallback(feedId, page = cursor)
        val items = page.items.applyPersistedLocalState()
        val entities = page.items.changedRemoteEntities()
        if (entities.isNotEmpty()) {
            feedDao.insertAll(entities)
        }
        AppLog.i(
            TAG,
            "loadNextPageOrThrow loaded feedId=$feedId cursor=${cursor.value} pageItems=${page.items.size} changed=${entities.size} " +
                "next=${page.nextCursor?.value} elapsed=${Clock.System.now().toEpochMilliseconds() - startedAt}ms",
        )
        feedCursorsFlow.update { it + (feedId to page.nextCursor) }
        savePagingState(
            sourceKey = feedId,
            nextCursor = page.nextCursor,
            pagesCount = page.totalPages
                ?: page.nextCursor?.value?.toIntOrNull()?.minus(1)
                ?: cursor.value.toIntOrNull(),
            completed = page.nextCursor == null,
        )
        return FeedPage(
            items = items,
            nextCursor = page.nextCursor,
            fromCache = false,
            updatedAt = page.updatedAt,
            totalPages = page.totalPages,
        )
    }

    suspend fun prefetchHabrHubArchive(feedId: String, pages: Int = 1) {
        if (!feedId.isHabrHubFeedId() || pages <= 0) return
        val syncState = feedDao.getSyncState(feedId)
        if (syncState != null && (syncState.status == "completed" || syncState.nextPage > 1)) {
            AppLog.i(
                TAG,
                "prefetchHabrHubArchive skip feedId=$feedId status=${syncState.status} next=${syncState.nextPage}",
            )
            return
        }
        val startedAt = Clock.System.now().toEpochMilliseconds()
        var cursor = PageCursor("1")
        AppLog.i(TAG, "prefetchHabrHubArchive start feedId=$feedId cursor=${cursor.value} pages=$pages")
        repeat(pages.coerceAtMost(1)) {
            val page = try {
                primarySource.getItems(feedId, cursor)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLog.w(TAG, "prefetchHabrHubArchive failed feedId=$feedId cursor=${cursor.value}", error)
                return
            }.onlyMatchingHabrHubItems(feedId)
            val entities = page.items.changedRemoteEntities()
            if (entities.isNotEmpty()) {
                feedDao.insertAll(entities)
            }
            feedCursorsFlow.update { it + (feedId to page.nextCursor) }
            savePagingState(
                sourceKey = feedId,
                nextCursor = page.nextCursor,
                pagesCount = page.totalPages
                    ?: page.nextCursor?.value?.toIntOrNull()?.minus(1)
                    ?: cursor.value.toIntOrNull(),
                completed = page.nextCursor == null,
            )
            AppLog.i(
                TAG,
                "prefetchHabrHubArchive page feedId=$feedId cursor=${cursor.value} items=${page.items.size} " +
                    "changed=${entities.size} next=${page.nextCursor?.value}",
            )
            cursor = page.nextCursor ?: return
        }
        AppLog.i(TAG, "prefetchHabrHubArchive done feedId=$feedId elapsed=${Clock.System.now().toEpochMilliseconds() - startedAt}ms")
    }

    /**
     * Check if more pages are available for a feed
     */
    fun hasMorePages(feedId: String): Boolean =
        feedId != HabrApiSource.FeedIds.AllCached && feedCursorsFlow.value[feedId] != null

    fun observeFeed(feedId: String): Flow<List<FeedItem>> = combine(
        if (feedId == HabrApiSource.FeedIds.AllCached) feedDao.getAllCached() else feedDao.getByFeed(feedId),
        feedDao.getArticleLocalStates(),
        feedDao.getFavoriteArticles(),
    ) { entities, localStates, favorites ->
        entities
            .map { entity -> entity.toDomain(json, localStates.byArticleId(), favorites.articleIds()) }
            .distinctBy { it.articleIdentityKey() }
    }.distinctUntilChanged()

    fun observeBookmarks(): Flow<List<FeedItem>> = combine(
        feedDao.getBookmarks(),
        feedDao.getArticleLocalStates(),
        feedDao.getFavoriteArticles(),
    ) { entities, localStates, favorites ->
        entities
            .map { entity -> entity.toDomain(json, localStates.byArticleId(), favorites.articleIds()) }
            .distinctBy { it.articleIdentityKey() }
    }.distinctUntilChanged()

    fun observeArticleItem(articleId: String): Flow<FeedItem?> = combine(
        feedDao.observeById(articleId),
        feedDao.getArticleLocalStates(),
        feedDao.getFavoriteArticles(),
    ) { entity, localStates, favorites ->
        entity?.toDomain(json, localStates.byArticleId(), favorites.articleIds())
    }.distinctUntilChanged()

    suspend fun getCachedFeed(feedId: String): List<FeedItem> {
        val localStates = feedDao.getArticleLocalStatesOnce().byArticleId()
        val favorites = feedDao.getFavoriteArticlesOnce().articleIds()
        val entities = if (feedId == HabrApiSource.FeedIds.AllCached) {
            feedDao.getAllCachedOnce()
        } else {
            feedDao.getByFeedOnce(feedId)
        }
        return entities.map { it.toDomain(json, localStates, favorites) }
            .distinctBy { it.articleIdentityKey() }
    }

    suspend fun isBookmarked(articleId: String): Boolean =
        feedDao.getFavoriteArticlesOnce().any { it.articleId == articleId }

    suspend fun getArticle(articleId: String): ArticleContent {
        val startedAt = Clock.System.now().toEpochMilliseconds()
        AppLog.i(TAG, "getArticle start articleId=$articleId")
        markRead(articleId)

        val entity = feedDao.getById(articleId)
            ?: throw SourceUnavailableException("Article not found: $articleId")

        return loadAndCacheArticle(entity).also { article ->
            AppLog.i(
                TAG,
                "getArticle loaded articleId=$articleId blocks=${article.blocks.size} " +
                    "elapsed=${Clock.System.now().toEpochMilliseconds() - startedAt}ms",
            )
        }
    }

    suspend fun getArticleComments(articleId: String): List<CommentNode> {
        val entity = feedDao.getById(articleId) ?: return emptyList()
        val source = articleContentSource as? ArticleCommentsSource ?: return emptyList()
        return try {
            source.getCommentsByUrl(entity.url)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getRelatedArticles(articleId: String, limit: Int = RELATED_ARTICLES_LIMIT): List<FeedItem> {
        val entity = feedDao.getById(articleId) ?: return emptyList()
        val localStates = feedDao.getArticleLocalStatesOnce().byArticleId()
        val favorites = feedDao.getFavoriteArticlesOnce().articleIds()
        val tagIds = entity.tags(json)
        val hubIds = entity.hubs(json)
        if (tagIds.isEmpty() && hubIds.isEmpty()) return emptyList()
        return feedDao.getByFeedOnce(entity.feedId)
            .asSequence()
            .filterNot { it.id == entity.id }
            .map { candidate ->
                val score = candidate.tags(json).count { it in tagIds } * TAG_MATCH_WEIGHT +
                    candidate.hubs(json).count { it in hubIds } * HUB_MATCH_WEIGHT
                candidate to score
            }
            .filter { (_, score) -> score > 0 }
            .sortedWith(
                compareByDescending<Pair<FeedItemEntity, Int>> { it.second }
                    .thenByDescending { it.first.publishedAtEpoch ?: it.first.fetchedAt },
            )
            .take(limit)
            .map { (candidate, _) -> candidate.toDomain(json, localStates, favorites) }
            .toList()
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
        val hub = url.normalizedHubSlug()
        val feedId = HabrApiSource.FeedIds.hub(hub)
        AppLog.i(TAG, "upsertCustomFeed id=$id title=$title hub=$hub feedId=$feedId")
        val feed = CustomFeedPreference(
            id = feedId,
            title = title.ifBlank { hub },
            url = hub.toHabrHubUrl(),
        )
        if (id != null && id != feedId) {
            preferencesRepository?.removeCustomFeed(id)
        }
        preferencesRepository?.upsertCustomFeed(feed)
        cachedFeeds = emptyList()
        getFeeds(forceRefresh = true)
    }

    suspend fun removeCustomFeed(id: String) {
        AppLog.i(TAG, "removeCustomFeed id=$id")
        preferencesRepository?.removeCustomFeed(id)
        if (id.startsWith(HabrApiSource.FeedIds.HubPrefix)) {
            val slug = id.removePrefix(HabrApiSource.FeedIds.HubPrefix)
                .substringBefore(':')
                .toHubSlug()
            preferencesRepository?.customFeeds()?.first()
                .orEmpty()
                .filter { it.url.normalizedHubSlug() == slug || it.id == "custom-hub-${slug.hashCode()}" }
                .forEach { preferencesRepository?.removeCustomFeed(it.id) }
        }
        cachedFeeds = emptyList()
        getFeeds(forceRefresh = true)
    }

    private suspend fun removeCustomHubFeed(slug: String) {
        val normalized = slug.normalizedHubSlug()
        val targetUrl = normalized.toHabrHubUrl()
        val targetFeedId = HabrApiSource.FeedIds.hub(normalized)
        preferencesRepository?.customFeeds()?.first()
            .orEmpty()
            .filter { preference ->
                preference.id == targetFeedId ||
                    preference.url == targetUrl ||
                    preference.url.normalizedHubSlug() == normalized ||
                    preference.id == "custom-hub-${normalized.hashCode()}"
            }
            .forEach { preferencesRepository?.removeCustomFeed(it.id) }
        cachedFeeds = emptyList()
    }

    fun observeFavoriteTagIds(): Flow<Set<String>> =
        feedDao.getFavoriteTags().map { tags -> tags.mapTo(mutableSetOf()) { it.tagId } }

    fun observeFavoriteHubIds(): Flow<Set<String>> =
        feedDao.getFavoriteHubs().map { hubs -> hubs.mapTo(mutableSetOf()) { it.hubId } }

    fun observeFavoriteTagTitles(): Flow<Map<String, String>> =
        feedDao.getFavoriteTags().map { tags -> tags.mapNotNull { it.title?.let { title -> it.tagId to title } }.toMap() }

    fun observeFavoriteHubTitles(): Flow<Map<String, String>> =
        feedDao.getFavoriteHubs().map { hubs -> hubs.mapNotNull { it.title?.let { title -> it.hubId to title } }.toMap() }

    suspend fun getFavoriteTagIds(): Set<String> =
        feedDao.getFavoriteTagsOnce().mapTo(mutableSetOf()) { it.tagId }

    suspend fun getFavoriteHubIds(): Set<String> =
        feedDao.getFavoriteHubsOnce().mapTo(mutableSetOf()) { it.hubId }

    suspend fun getFavoriteTagTitles(): Map<String, String> =
        feedDao.getFavoriteTagsOnce().mapNotNull { it.title?.let { title -> it.tagId to title } }.toMap()

    suspend fun getFavoriteHubTitles(): Map<String, String> =
        feedDao.getFavoriteHubsOnce().mapNotNull { it.title?.let { title -> it.hubId to title } }.toMap()

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
        val slug = hubId.removePrefix("hub-").toHubSlug()
        AppLog.i(TAG, "toggleFavoriteHub hubId=$hubId title=$title slug=$slug")
        if (feedDao.getFavoriteHubsOnce().any { it.hubId == hubId }) {
            feedDao.deleteFavoriteHub(hubId)
            removeCustomHubFeed(slug)
        } else {
            feedDao.insertFavoriteHub(
                FavoriteHubEntity(
                    hubId = hubId,
                    title = title ?: slug,
                    createdAt = Clock.System.now().toEpochMilliseconds(),
                )
            )
            upsertCustomFeed(id = null, title = slug, url = slug)
        }
    }

    fun requireFirstFeedId(): String {
        return cachedFeeds.firstOrNull()?.id
            ?: throw SourceUnavailableException("No feed descriptors are available.")
    }

    private suspend fun isCacheFresh(feedId: String): Boolean {
        val fetchedAt = feedDao.getNewestFetchedAtByFeed(feedId) ?: return false
        return Clock.System.now().toEpochMilliseconds() - fetchedAt < FEED_REFRESH_TTL_MILLIS
    }

    private suspend fun cachedFeedOrThrow(
        feedId: String,
        cached: List<FeedItem>,
        nextCursor: PageCursor?,
        error: Exception,
    ): FeedPage {
        if (cached.isEmpty()) throw error
        feedCursorsFlow.update { it + (feedId to nextCursor) }
        return FeedPage(
            items = cached,
            nextCursor = nextCursor,
            fromCache = true,
            updatedAt = feedDao.getNewestFetchedAtByFeed(feedId)?.toString(),
        )
    }

    private suspend fun nullIfCachedFeedExistsOrThrow(feedId: String, error: Exception): FeedPage? {
        if (getCachedFeed(feedId).isEmpty()) throw error
        feedCursorsFlow.update { it + (feedId to null) }
        return null
    }

    private fun allCachedDescriptor(): FeedDescriptor = FeedDescriptor(
        id = HabrApiSource.FeedIds.AllCached,
        title = "Все загруженные",
        sourceTitle = "Room",
        url = "local://all-loaded",
        description = "Все уникальные статьи, уже загруженные в локальную базу",
        kind = FeedKind.All,
    )

    private fun maxCursor(first: PageCursor?, second: PageCursor?): PageCursor? {
        val firstPage = first?.value?.toIntOrNull()
        val secondPage = second?.value?.toIntOrNull()
        return when {
            firstPage == null -> second
            secondPage == null -> first
            firstPage >= secondPage -> first
            else -> second
        }
    }

    private suspend fun savePagingState(
        sourceKey: String,
        nextCursor: PageCursor?,
        pagesCount: Int?,
        completed: Boolean,
    ) {
        val now = Clock.System.now().toEpochMilliseconds()
        val current = feedDao.getSyncState(sourceKey)
        feedDao.upsertSyncState(
            SyncStateEntity(
                sourceKey = sourceKey,
                mode = "paging",
                status = if (completed) "completed" else "ready",
                nextPage = nextCursor?.value?.toIntOrNull() ?: ((current?.nextPage ?: 1).coerceAtLeast(1)),
                pagesCountSnapshot = pagesCount ?: current?.pagesCountSnapshot,
                pagesProcessed = current?.pagesProcessed ?: 0,
                receivedCount = current?.receivedCount ?: 0,
                uniqueCount = current?.uniqueCount ?: 0,
                failedPage = null,
                errorCode = null,
                startedAtEpochMillis = current?.startedAtEpochMillis ?: now,
                updatedAtEpochMillis = now,
                completedAtEpochMillis = if (completed) now else null,
            ),
        )
    }

    private fun SyncStateEntity.toPageCursor(): PageCursor? =
        if (status == "completed") null else PageCursor(nextPage.coerceAtLeast(1).toString())

    private suspend fun List<FeedItem>.changedRemoteEntities(): List<FeedItemEntity> {
        val now = Clock.System.now().toEpochMilliseconds()
        val currentById = feedDao.getAllCachedOnce().associateBy { it.id }
        return mapNotNull { item ->
            val current = currentById[item.id]
            val next = item.toEntity(
                json = json,
                cachedArticleJson = current?.cachedArticleJson,
                fetchedAt = current?.fetchedAt ?: now,
            )
            when {
                current == null -> next.copy(fetchedAt = now)
                current.hasSameRemoteData(next) -> null
                else -> next.copy(fetchedAt = now)
            }
        }
    }

    private suspend fun loadAndCacheArticle(entity: FeedItemEntity): ArticleContent {
        val cachedArticle = entity.cachedArticleJson?.let { cached ->
            runCatching { json.decodeFromString<ArticleContent>(cached) }.getOrNull()
        }
        val fallback = cachedArticle ?: buildArticleContent(entity)

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
                val merged = fallback.copy(
                    imageUrl = fallback.imageUrl ?: fullArticle.imageUrl,
                    blocks = fullArticle.blocks,
                    sourceNotice = fullArticle.sourceNotice,
                    author = fallback.author ?: fullArticle.author,
                    hubs = fallback.hubs.mergeHubSlugs(fullArticle.hubs),
                    tags = fallback.tags.ifEmpty { fullArticle.tags },
                )
                feedDao.update(entity.copy(cachedArticleJson = json.encodeToString(merged)))
                merged
            }
            else -> fallback
        }
    }

    private fun List<Hub>.mergeHubSlugs(hubsWithSlugs: List<Hub>): List<Hub> {
        if (hubsWithSlugs.isEmpty()) return this
        if (isEmpty()) return hubsWithSlugs
        val byId = hubsWithSlugs.associateBy { it.id }
        val byTitle = hubsWithSlugs.associateBy { it.title.lowercase() }
        return map { hub ->
            val withSlug = byId[hub.id] ?: byTitle[hub.title.lowercase()]
            if (hub.slug.isNullOrBlank() && !withSlug?.slug.isNullOrBlank()) {
                hub.copy(slug = withSlug.slug)
            } else {
                hub
            }
        }
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

    @Suppress("TooGenericExceptionCaught", "ThrowsCount")
    private suspend fun loadFeedPageWithFallback(feedId: String, page: PageCursor?): FeedPage {
        if (feedId.isHabrHubFeedId()) {
            return loadHabrHubPage(feedId, page)
        }
        val primary = sourceFor(feedId)
        val startedAt = Clock.System.now().toEpochMilliseconds()
        AppLog.d(TAG, "loadFeedPage primary=${primary.sourceName()} feedId=$feedId page=${page?.value ?: 1}")
        return try {
            primary.getItems(feedId, page).also { result ->
                AppLog.i(
                    TAG,
                    "loadFeedPage primary success source=${primary.sourceName()} feedId=$feedId items=${result.items.size} " +
                        "fromCache=${result.fromCache} elapsed=${Clock.System.now().toEpochMilliseconds() - startedAt}ms",
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (primaryError: Exception) {
            if (primaryError.isNonFallbackHabrError()) throw primaryError
            AppLog.w(
                TAG,
                "loadFeedPage primary failed source=${primary.sourceName()} feedId=$feedId page=${page?.value ?: 1}; trying fallbacks",
                primaryError,
            )
            fallbackSourcesByFeedId[feedId].orEmpty()
                .filterNot { it == primary }
                .firstNotNullOfOrNull { fallbackSource ->
                    try {
                        fallbackSource.getItems(feedId, page).also { result ->
                            AppLog.i(
                                TAG,
                                "loadFeedPage fallback success source=${fallbackSource.sourceName()} feedId=$feedId " +
                                    "items=${result.items.size} fromCache=${result.fromCache} " +
                                    "elapsed=${Clock.System.now().toEpochMilliseconds() - startedAt}ms",
                            )
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (fallbackError: Exception) {
                        AppLog.w(
                            TAG,
                            "loadFeedPage fallback failed source=${fallbackSource.sourceName()} feedId=$feedId",
                            fallbackError,
                        )
                        null
                    }
                }
                ?: throw primaryError
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun loadHabrHubPage(feedId: String, page: PageCursor?): FeedPage {
        val startedAt = Clock.System.now().toEpochMilliseconds()
        if (page != null) {
            AppLog.d(TAG, "loadHabrHubPage api archive feedId=$feedId page=${page.value}")
            return primarySource.getItems(feedId, page).onlyMatchingHabrHubItems(feedId).also { result ->
                AppLog.i(
                    TAG,
                    "loadHabrHubPage api archive success feedId=$feedId page=${page.value} items=${result.items.size} " +
                        "next=${result.nextCursor?.value} elapsed=${Clock.System.now().toEpochMilliseconds() - startedAt}ms",
                )
            }
        }

        val rssSource = customRssSource
        if (rssSource != null) {
            try {
                AppLog.d(TAG, "loadHabrHubPage rss latest feedId=$feedId")
                val rssPage = rssSource.getItems(feedId, null)
                if (rssPage.items.isEmpty() && rssPage.fromCache) {
                    AppLog.w(TAG, "loadHabrHubPage rss latest unavailable feedId=$feedId; falling back to api")
                } else {
                    return rssPage.copy(nextCursor = PageCursor("1")).onlyMatchingHabrHubItems(feedId).also { result ->
                        AppLog.i(
                            TAG,
                            "loadHabrHubPage rss latest success feedId=$feedId items=${result.items.size} " +
                                "next=${result.nextCursor?.value} elapsed=${Clock.System.now().toEpochMilliseconds() - startedAt}ms",
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLog.w(TAG, "loadHabrHubPage rss latest failed feedId=$feedId; falling back to api", error)
            }
        }

        return primarySource.getItems(feedId, null).onlyMatchingHabrHubItems(feedId).also { result ->
            AppLog.i(
                TAG,
                "loadHabrHubPage api latest fallback success feedId=$feedId items=${result.items.size} " +
                    "next=${result.nextCursor?.value} elapsed=${Clock.System.now().toEpochMilliseconds() - startedAt}ms",
            )
        }
    }

    private fun Exception.isNonFallbackHabrError(): Boolean =
        this is HabrRemoteException.BadRequest ||
            this is HabrRemoteException.Validation

    private fun FeedSource.sourceName(): String = this::class.simpleName ?: "FeedSource"

    private fun String.isHabrHubFeedId(): Boolean = startsWith(HabrApiSource.FeedIds.HubPrefix)

    private fun String.habrHubSlugOrNull(): String? =
        takeIf { it.isHabrHubFeedId() }
            ?.removePrefix(HabrApiSource.FeedIds.HubPrefix)
            ?.substringBefore(':')
            ?.toHubSlug()
            ?.takeIf { slug -> slug.isNotBlank() }

    private fun FeedPage.onlyMatchingHabrHubItems(feedId: String): FeedPage {
        val hubSlug = feedId.habrHubSlugOrNull() ?: return this
        val filtered = items.filter { item -> item.belongsToHub(hubSlug) }
        if (filtered.size != items.size) {
            AppLog.w(
                TAG,
                "filtered non-matching hub articles feedId=$feedId hub=$hubSlug before=${items.size} after=${filtered.size}",
            )
        }
        return copy(items = filtered)
    }

    private fun FeedItem.belongsToHub(hubSlug: String): Boolean =
        hubs.any { hub -> hub.matchesHubSlug(hubSlug) }

    private fun Hub.matchesHubSlug(hubSlug: String): Boolean {
        val normalizedSlug = hubSlug.toHubSlug()
        return id.toHubSlug() == normalizedSlug ||
            slug?.toHubSlug() == normalizedSlug ||
            title.toHubSlug() == normalizedSlug
    }

    private suspend fun List<FeedItem>.applyPersistedLocalState(): List<FeedItem> {
        val localStates = feedDao.getArticleLocalStatesOnce().byArticleId()
        val favorites = feedDao.getFavoriteArticlesOnce().articleIds()
        return map { item -> item.withLocalState(localStates, favorites) }
    }

    private fun CustomFeedPreference.toDescriptor(): FeedDescriptor {
        val hub = url.normalizedHubSlug()
        val feedId = HabrApiSource.FeedIds.hub(hub, HabrPeriod.AllTime)
        return FeedDescriptor(
            id = feedId,
            title = title.ifBlank { hub },
            sourceTitle = "Habr API",
            url = hub.toHabrHubUrl(),
            description = "Архив хаба через /kek/v2/articles?hub=$hub&period=alltime",
            kind = FeedKind.Hub,
        )
    }

    private fun String.normalizedHubSlug(): String = toHubSlug()

    private fun String.toHubSlug(): String {
        val value = trim().replace("&amp;", "&").trimEnd('/')
        val slug = when {
            "/hubs/" in value -> value.substringAfterLast("/hubs/")
            "/hub/" in value -> value.substringAfterLast("/hub/")
            else -> value
        }
        return slug
            .substringBefore('/')
            .substringBefore('?')
            .trim()
            .replace(Regex("\\s+"), "_")
            .lowercase()
    }

    private fun FeedDescriptor.isHabrApiFeed(): Boolean = id.startsWith(HabrApiSource.FeedIds.HubPrefix)

    private fun FeedDescriptor.toHabrHubRssDescriptor(): FeedDescriptor? {
        if (!isHabrApiFeed()) return null
        val slug = url.toHubSlug().takeIf { it.isNotBlank() }
            ?: id.removePrefix(HabrApiSource.FeedIds.HubPrefix).substringBefore(':').takeIf { it.isNotBlank() }
            ?: return null
        return copy(
            sourceTitle = "Habr RSS",
            url = slug.toHabrHubRssUrl(),
            description = "RSS fallback for Habr hub $slug",
        )
    }

    private fun String.toHabrHubUrl(): String =
        "https://habr.com/ru/hubs/$this/"

    private fun String.toHabrHubRssUrl(): String =
        "https://habr.com/ru/rss/hub/$this/all/?fl=ru&with_hubs=true&with_tags=true"

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
    fetchedAt: Long = Clock.System.now().toEpochMilliseconds(),
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
    fetchedAt = fetchedAt,
)

private fun FeedItemEntity.hasSameRemoteData(other: FeedItemEntity): Boolean =
    copy(cachedArticleJson = null, fetchedAt = 0L) == other.copy(cachedArticleJson = null, fetchedAt = 0L)

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

private fun FeedItem.articleIdentityKey(): String {
    val normalizedUrl = url
        .trim()
        .lowercase()
        .substringBefore("#")
        .substringBefore("?")
        .trimEnd('/')
    return normalizedUrl.ifBlank { title.trim().lowercase() }
}

private fun List<ArticleLocalStateEntity>.byArticleId(): Map<String, ArticleLocalStateEntity> =
    associateBy { it.articleId }

private fun List<FavoriteArticleEntity>.articleIds(): Set<String> =
    mapTo(mutableSetOf()) { it.articleId }

private fun FeedItemEntity.tags(json: Json): Set<String> =
    runCatching { json.decodeFromString<List<Tag>>(tagsJson).mapTo(mutableSetOf()) { it.id } }.getOrDefault(emptySet())

private fun FeedItemEntity.hubs(json: Json): Set<String> =
    runCatching { json.decodeFromString<List<Hub>>(hubsJson).mapTo(mutableSetOf()) { it.id } }.getOrDefault(emptySet())

private const val RELATED_ARTICLES_LIMIT = 6
private const val TAG_MATCH_WEIGHT = 2
private const val HUB_MATCH_WEIGHT = 1
private const val FEED_REFRESH_TTL_MILLIS = 60L * 60L * 1_000L
private const val TAG = "Repository"
