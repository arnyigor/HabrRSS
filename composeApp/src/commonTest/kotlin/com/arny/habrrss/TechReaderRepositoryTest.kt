package com.arny.habrrss

import com.arny.habrrss.data.api.HabrApiSource
import com.arny.habrrss.data.database.InMemoryFeedDao
import com.arny.habrrss.data.preferences.DefaultPreferencesRepository
import com.arny.habrrss.data.remote.habr.error.HabrRemoteException
import com.arny.habrrss.data.repository.TechReaderRepository
import com.arny.habrrss.data.rss.GenericRssSource
import com.arny.habrrss.domain.models.ArticleBlock
import com.arny.habrrss.domain.models.ArticleContent
import com.arny.habrrss.domain.models.Author
import com.arny.habrrss.domain.models.CommentNode
import com.arny.habrrss.domain.models.FeedDescriptor
import com.arny.habrrss.domain.models.FeedItem
import com.arny.habrrss.domain.models.FeedKind
import com.arny.habrrss.domain.models.FeedPage
import com.arny.habrrss.domain.models.Hub
import com.arny.habrrss.domain.models.InlineNode
import com.arny.habrrss.domain.models.PageCursor
import com.arny.habrrss.domain.models.Tag
import com.arny.habrrss.domain.source.ArticleContentSource
import com.arny.habrrss.domain.source.FeedSource
import com.arny.habrrss.domain.source.SourceUnavailableException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TechReaderRepositoryTest {
    @Test
    fun refreshKeepsReadAndBookmarkState() = runTest {
        val repository = TechReaderRepository(
            primarySource = FakeFeedSource(),
            feedDao = InMemoryFeedDao(),
        )

        repository.getFeeds()
        repository.refreshFeed("feed")
        repository.getArticle("kotlin")
        repository.toggleBookmark("compose")

        val refreshed = repository.refreshFeed("feed")

        assertTrue(refreshed.items.first { it.id == "kotlin" }.isRead)
        assertTrue(refreshed.items.first { it.id == "compose" }.isBookmarked)
        assertFalse(refreshed.items.first { it.id == "kotlin" }.isBookmarked)
    }

    @Test
    fun refreshWritesOnlyNewOrChangedItemsAndKeepsLocalHistory() = runTest {
        val dao = InMemoryFeedDao()
        val source = MutableRemoteFeedSource(
            listOf(
                remoteItem(id = "one", title = "Old title"),
                remoteItem(id = "stale", title = "Keep me"),
            ),
        )
        val repository = TechReaderRepository(primarySource = source, feedDao = dao)

        repository.refreshFeed("feed")
        val firstFetchedAt = dao.getById("one")?.fetchedAt
        repository.refreshFeed("feed")

        assertEquals(firstFetchedAt, dao.getById("one")?.fetchedAt)

        source.items = listOf(remoteItem(id = "one", title = "New title"))
        repository.refreshFeed("feed", force = true)

        assertEquals("New title", dao.getById("one")?.title)
        assertNotNull(dao.getById("stale"))
    }

    @Test
    fun allCachedFeedContainsLoadedArticlesFromAnySource() = runTest {
        val source = MutableRemoteFeedSource(
            listOf(
                remoteItem(id = "one", title = "One"),
                remoteItem(id = "two", title = "Two"),
            ),
        )
        val repository = TechReaderRepository(
            primarySource = source,
            feedDao = InMemoryFeedDao(),
        )

        repository.refreshFeed("feed")
        val cached = repository.getCachedFeed(HabrApiSource.FeedIds.AllCached)

        assertEquals(setOf("one", "two"), cached.map { it.id }.toSet())
    }

    @Test
    fun favoriteHubAddsAndRemovesHubFeed() = runTest {
        val preferencesRepository = DefaultPreferencesRepository()
        val customRssSource = GenericRssSource(emptyList())
        val repository = TechReaderRepository(
            primarySource = FakeFeedSource(),
            feedDao = InMemoryFeedDao(),
            customRssSource = customRssSource,
            preferencesRepository = preferencesRepository,
        )

        repository.getFeeds()
        repository.toggleFavoriteHub(hubId = "android", title = "Android")

        assertTrue(repository.getFeeds(forceRefresh = true).any { it.id == "habr-hub:android:alltime" && it.url.contains("/hubs/android/") })

        repository.toggleFavoriteHub(hubId = "android", title = "Android")

        assertFalse(repository.getFeeds(forceRefresh = true).any { it.id == "habr-hub:android:alltime" })
    }

    @Test
    fun customFeedInputIsHabrHubSlug() = runTest {
        val preferencesRepository = DefaultPreferencesRepository()
        val customRssSource = GenericRssSource(emptyList())
        val repository = TechReaderRepository(
            primarySource = FakeFeedSource(),
            feedDao = InMemoryFeedDao(),
            customRssSource = customRssSource,
            preferencesRepository = preferencesRepository,
        )

        repository.upsertCustomFeed(id = null, title = "", url = " android ")
        val customFeed = repository.getFeeds(forceRefresh = true).first { it.id == "habr-hub:android:alltime" }

        assertEquals("android", customFeed.title)
        assertEquals("https://habr.com/ru/hubs/android/", customFeed.url)
        assertEquals(FeedKind.Hub, customFeed.kind)
    }

    @Test
    fun editingHubFeedChangesApiFeedIdAndRemovesOldHub() = runTest {
        val preferencesRepository = DefaultPreferencesRepository()
        val repository = TechReaderRepository(
            primarySource = FakeFeedSource(),
            feedDao = InMemoryFeedDao(),
            customRssSource = GenericRssSource(emptyList()),
            preferencesRepository = preferencesRepository,
        )

        repository.upsertCustomFeed(id = null, title = "Android", url = "android")
        repository.upsertCustomFeed(id = "habr-hub:android:alltime", title = "Kotlin", url = "kotlin")
        val feeds = repository.getFeeds(forceRefresh = true)

        assertFalse(feeds.any { it.id == "habr-hub:android:alltime" })
        assertTrue(feeds.any { it.id == "habr-hub:kotlin:alltime" && it.title == "Kotlin" })
    }

    @Test
    fun habrHubCustomFeedUsesRssLatestAndApiArchivePrefetch() = runTest {
        val preferencesRepository = DefaultPreferencesRepository()
        val feedId = HabrApiSource.FeedIds.hub("android_dev")
        val primarySource = MutableRemoteFeedSource(
            items = listOf(
                habrApiItem(feedId = feedId, articleId = "1066094", title = "API duplicate", publishedAtEpoch = 90),
                habrApiItem(feedId = feedId, articleId = "1000000", title = "API archive", publishedAtEpoch = 10),
            ),
            nextCursor = PageCursor("2"),
        )
        val repository = TechReaderRepository(
            primarySource = primarySource,
            feedDao = InMemoryFeedDao(),
            customRssSource = GenericRssSource(mockRssClient()),
            preferencesRepository = preferencesRepository,
        )

        repository.upsertCustomFeed(id = null, title = "Android", url = "android_dev")
        repository.getFeeds(forceRefresh = true)
        val page = repository.refreshFeed(feedId, force = true)
        repository.prefetchHabrHubArchive(feedId)
        repository.prefetchHabrHubArchive(feedId)
        val cached = repository.getCachedFeed(feedId)

        assertEquals(
            listOf("$feedId:habr-1066224", "$feedId:habr-1066094", "$feedId:habr-1000000"),
            cached.map { it.id },
        )
        assertEquals("$feedId:habr-1066224", page.items.first().id)
        assertEquals("Fresh RSS Android article", page.items.first().title)
        assertEquals("https://habr.com/ru/articles/1066224/", page.items.first().url)
        assertEquals(listOf("Разработка мобильных приложений"), page.items.first().hubs.map { it.title })
        assertEquals(listOf("MDM", "Android Enterprise"), page.items.first().tags.map { it.title })
        assertEquals("API duplicate", cached.first { it.id == "$feedId:habr-1066094" }.title)
        assertEquals(1, primarySource.getItemsCalls)

        repository.refreshFeed(feedId, force = true)
        repository.prefetchHabrHubArchive(feedId)

        assertEquals(2, primarySource.getItemsCalls)
    }

    @Test
    fun habrHubFeedDropsApiArticlesWithoutSelectedHub() = runTest {
        val feedId = HabrApiSource.FeedIds.hub("android")
        val source = MutableRemoteFeedSource(
            items = listOf(
                remoteItem(id = "android-article", title = "Android").copy(
                    hubs = listOf(Hub(id = "android", title = "Android", slug = "android")),
                ),
                remoteItem(id = "desktop-article", title = "Desktop").copy(
                    hubs = listOf(Hub(id = "desktop", title = "Desktop", slug = "desktop")),
                ),
            ),
            nextCursor = PageCursor("2"),
        )
        val repository = TechReaderRepository(
            primarySource = source,
            feedDao = InMemoryFeedDao(),
        )

        val page = repository.refreshFeed(feedId, force = true)
        val cached = repository.getCachedFeed(feedId)

        assertEquals(listOf("android-article"), page.items.map { it.id })
        assertEquals(listOf("android-article"), cached.map { it.id })
        assertTrue(repository.getCachedFeed(HabrApiSource.FeedIds.AllCached).none { it.id == "desktop-article" })
    }

    @Test
    fun loadAllRemainingPagesLoadsAllPagesAndStops() = runTest {
        val feedId = HabrApiSource.FeedIds.hub("android_test")
        val source = PagedHabrHubSource(
            listOf(
                hubPage(feedId, page = 1, count = 3),
                hubPage(feedId, page = 2, count = 3),
                hubPage(feedId, page = 3, count = 3),
            ),
        )
        val repository = TechReaderRepository(
            primarySource = source,
            feedDao = InMemoryFeedDao(),
        )

        repository.refreshFeed(feedId, force = true)
        val progress = mutableListOf<Pair<Int, Int?>>()
        val loaded = repository.loadAllRemainingPages(feedId) { processed, total ->
            progress += processed to total
        }

        // refreshFeed already persisted page 1, so the cumulative progress starts at 2.
        assertEquals(2, loaded)
        assertEquals(listOf<Pair<Int, Int?>>(2 to 3, 3 to 3), progress)
        assertEquals(9, repository.getCachedFeed(feedId).size)
        assertEquals(3, source.getItemsCalls)
        assertFalse(repository.hasMorePages(feedId))
    }

    @Test
    fun loadAllRemainingPagesCancellationKeepsLoadedPages() = runTest {
        val feedId = HabrApiSource.FeedIds.hub("android_test")
        val source = PagedHabrHubSource(
            pages = List(50) { hubPage(feedId, page = it + 1, count = 2) },
            delayMillis = 10,
        )
        val repository = TechReaderRepository(
            primarySource = source,
            feedDao = InMemoryFeedDao(),
        )

        repository.refreshFeed(feedId, force = true)
        val job = launch {
            repository.loadAllRemainingPages(feedId)
        }
        delay(25)
        job.cancelAndJoin()

        val cached = repository.getCachedFeed(feedId)
        assertTrue(cached.isNotEmpty(), "cancelled import should keep already loaded pages")
        assertTrue(cached.size < 100, "cancellation should stop before all pages, got ${cached.size}")
        assertTrue(repository.hasMorePages(feedId))
    }

    @Test
    fun loadAllRemainingPagesPreservesCursorOnApiError() = runTest {
        val feedId = HabrApiSource.FeedIds.hub("android_test")
        val source = FailingAtPageHubSource(
            pages = listOf(
                hubPage(feedId, page = 1, count = 3),
                hubPage(feedId, page = 2, count = 3),
                hubPage(feedId, page = 3, count = 3),
            ),
            failAtPage = 2,
            failuresLeft = 1,
        )
        val repository = TechReaderRepository(
            primarySource = source,
            feedDao = InMemoryFeedDao(),
        )

        repository.refreshFeed(feedId, force = true)

        val error = assertFailsWith<HabrRemoteException.RateLimited> {
            repository.loadAllRemainingPages(feedId)
        }
        assertEquals(30L, error.retryAfterSeconds)

        // The already fetched page stays in the DB and the paging cursor is preserved,
        // so the "load all pages" button remains available for a retry.
        assertEquals(3, repository.getCachedFeed(feedId).size)
        assertTrue(repository.hasMorePages(feedId))

        // A retry continues from the failed page instead of restarting or dying.
        repository.loadAllRemainingPages(feedId)
        assertEquals(9, repository.getCachedFeed(feedId).size)
        assertFalse(repository.hasMorePages(feedId))
    }

    @Test
    fun loadAllRemainingPagesProgressContinuesFromPersistedCursor() = runTest {
        val feedId = HabrApiSource.FeedIds.hub("android_test")
        val source = FailingAtPageHubSource(
            pages = listOf(
                hubPage(feedId, page = 1, count = 3),
                hubPage(feedId, page = 2, count = 3),
                hubPage(feedId, page = 3, count = 3),
            ),
            failAtPage = 2,
            failuresLeft = 1,
        )
        val repository = TechReaderRepository(
            primarySource = source,
            feedDao = InMemoryFeedDao(),
        )

        repository.refreshFeed(feedId, force = true)
        val firstProgress = mutableListOf<Pair<Int, Int?>>()
        assertFailsWith<HabrRemoteException.RateLimited> {
            repository.loadAllRemainingPages(feedId) { processed, total ->
                firstProgress += processed to total
            }
        }

        // Page 2 fails before any progress callback: page 1 is already persisted (via refresh),
        // the cursor points at page 2, so the stored progress is "1 из 3", not "0".
        assertEquals(emptyList(), firstProgress)
        val afterFailure = repository.loadAllPagesProgress(feedId)
        assertEquals(1, afterFailure.pagesProcessed)
        assertEquals(3, afterFailure.totalPages)

        // Restarting the import continues from the persisted position instead of zero.
        val secondProgress = mutableListOf<Pair<Int, Int?>>()
        repository.loadAllRemainingPages(feedId) { processed, total ->
            secondProgress += processed to total
        }
        assertEquals(listOf<Pair<Int, Int?>>(2 to 3, 3 to 3), secondProgress)
        assertFalse(repository.hasMorePages(feedId))

        // Fully imported: progress equals the total even though the cursor is now null.
        val afterComplete = repository.loadAllPagesProgress(feedId)
        assertEquals(3, afterComplete.pagesProcessed)
        assertEquals(3, afterComplete.totalPages)
    }

    @Test
    fun hubRefreshAfterLoadAllAccumulatesWithoutDuplicates() = runTest {
        val feedId = HabrApiSource.FeedIds.hub("android_test")
        val source = PagedHabrHubSource(
            listOf(
                hubPage(feedId, page = 1, count = 3),
                hubPage(feedId, page = 2, count = 3),
                hubPage(feedId, page = 3, count = 3),
            ),
        )
        val repository = TechReaderRepository(
            primarySource = source,
            feedDao = InMemoryFeedDao(),
        )

        repository.refreshFeed(feedId, force = true)
        repository.loadAllRemainingPages(feedId)
        val callsAfterLoadAll = source.getItemsCalls

        // Force refresh must not wipe the accumulated archive (old behavior deleted by feed).
        repository.refreshFeed(feedId, force = true)
        val cached = repository.getCachedFeed(feedId)

        assertEquals(9, cached.size)
        assertEquals(9, cached.map { it.id }.toSet().size)
        assertTrue(source.getItemsCalls > callsAfterLoadAll)

        // A non-forced refresh hits the fresh-cache shortcut and does not refetch.
        val callsBeforeCachedRefresh = source.getItemsCalls
        repository.refreshFeed(feedId, force = false)
        assertEquals(callsBeforeCachedRefresh, source.getItemsCalls)
        assertEquals(9, repository.getCachedFeed(feedId).size)
    }


    @Test
    fun getArticleUsesFullArticleFromArticleContentSource() = runTest {
        val feedDao = InMemoryFeedDao()
        val repository = TechReaderRepository(
            primarySource = FakeFeedSource(),
            feedDao = feedDao,
            articleContentSource = FakeArticleContentSource(),
        )

        repository.refreshFeed("feed")
        val article = repository.getArticle("kotlin")

        assertEquals("Full article from content source.", article.sourceNotice)
        assertEquals("Full KMP article body", (article.blocks.single() as ArticleBlock.Paragraph).inline.plain())
    }

    @Test
    fun getRelatedArticlesReturnsMatchesSortedByScoreAndDate() = runTest {
        val source = MutableRemoteFeedSource(
            listOf(
                relatedItem(id = "base", tags = listOf("kotlin", "compose"), hubs = listOf("android"), publishedAtEpoch = 100),
                relatedItem(id = "strong", tags = listOf("kotlin", "compose"), hubs = emptyList(), publishedAtEpoch = 200),
                relatedItem(id = "tag-match", tags = listOf("kotlin"), hubs = emptyList(), publishedAtEpoch = 150),
                relatedItem(id = "hub-match", tags = emptyList(), hubs = listOf("android"), publishedAtEpoch = 300),
                relatedItem(id = "unrelated", tags = listOf("cpp"), hubs = emptyList(), publishedAtEpoch = 400),
            ),
        )
        val repository = TechReaderRepository(
            primarySource = source,
            feedDao = InMemoryFeedDao(),
        )

        repository.refreshFeed("feed")
        val related = repository.getRelatedArticles("base", limit = 3)

        assertEquals(listOf("strong", "tag-match", "hub-match"), related.map { it.id })
        assertTrue(related.none { it.id == "unrelated" })
    }

    @Test
    fun getRelatedArticlesReturnsEmptyWhenNoOverlap() = runTest {
        val source = MutableRemoteFeedSource(
            listOf(
                relatedItem(id = "base", tags = listOf("kotlin"), hubs = emptyList(), publishedAtEpoch = 100),
                relatedItem(id = "other", tags = listOf("cpp"), hubs = emptyList(), publishedAtEpoch = 200),
            ),
        )
        val repository = TechReaderRepository(
            primarySource = source,
            feedDao = InMemoryFeedDao(),
        )

        repository.refreshFeed("feed")
        val related = repository.getRelatedArticles("base")

        assertTrue(related.isEmpty())
    }

    @Test
    fun getArticleCommentsReturnsEmptyWhenSourceDoesNotSupportComments() = runTest {
        val repository = TechReaderRepository(
            primarySource = FakeFeedSource(),
            feedDao = InMemoryFeedDao(),
            articleContentSource = FakeArticleContentSource(),
        )

        repository.refreshFeed("feed")
        val comments = repository.getArticleComments("kotlin")

        assertTrue(comments.isEmpty())
    }

    @Test
    fun getArticleUsesCachedFullArticleWhenNetworkFails() = runTest {
        val contentSource = MutableFakeArticleContentSource()
        val repository = TechReaderRepository(
            primarySource = FakeFeedSource(),
            feedDao = InMemoryFeedDao(),
            articleContentSource = contentSource,
        )

        repository.refreshFeed("feed")
        val onlineArticle = repository.getArticle("kotlin")
        contentSource.fail = true
        repository.refreshFeed("feed")
        val offlineArticle = repository.getArticle("kotlin")

        assertEquals("Full article from content source.", onlineArticle.sourceNotice)
        assertEquals("Full article from content source.", offlineArticle.sourceNotice)
        assertEquals(
            "Full KMP article body",
            (offlineArticle.blocks.single() as ArticleBlock.Paragraph).inline.plain(),
        )
    }
}

internal class MutableRemoteFeedSource(
    var items: List<FeedItem>,
    private val nextCursor: PageCursor? = null,
) : FeedSource {
    var getItemsCalls: Int = 0
        private set

    override suspend fun getFeeds(): List<FeedDescriptor> = listOf(
        FeedDescriptor(
            id = "feed",
            title = "Feed",
            sourceTitle = "Fake",
            url = "https://example.com/rss",
            description = "Fake feed",
            kind = FeedKind.Custom,
        ),
    )

    override suspend fun getItems(feedId: String, page: PageCursor?): FeedPage {
        getItemsCalls += 1
        return FeedPage(
            items = items.map { it.copy(feedId = feedId) },
            nextCursor = nextCursor,
            fromCache = false,
            updatedAt = "2026-05-01",
        )
    }

    @Deprecated("Use ArticleContentSource instead")
    override suspend fun getArticle(articleId: String): ArticleContent = error("Use ArticleContentSource")

    override suspend fun getComments(articleId: String): List<CommentNode> = emptyList()
}

private class FailingHabrHubSource : FeedSource {
    var getItemsCalls: Int = 0
        private set

    override suspend fun getFeeds(): List<FeedDescriptor> = emptyList()

    override suspend fun getItems(feedId: String, page: PageCursor?): FeedPage {
        getItemsCalls += 1
        throw SourceUnavailableException("Primary API failed")
    }

    @Deprecated("Use ArticleContentSource instead")
    override suspend fun getArticle(articleId: String): ArticleContent = error("Use ArticleContentSource")

    override suspend fun getComments(articleId: String): List<CommentNode> = emptyList()
}

/**
 * Paginated Habr hub archive source. [page] 1 is returned for `page == null` (latest call),
 * subsequent [PageCursor] values map to pages 2..N; the last page reports `nextCursor = null`.
 */
private class PagedHabrHubSource(
    private val pages: List<List<FeedItem>>,
    private val delayMillis: Long = 0L,
) : FeedSource {
    var getItemsCalls: Int = 0
        private set

    override suspend fun getFeeds(): List<FeedDescriptor> = emptyList()

    override suspend fun getItems(feedId: String, page: PageCursor?): FeedPage {
        getItemsCalls += 1
        if (delayMillis > 0) delay(delayMillis)
        val index = page?.value?.toIntOrNull()?.minus(1) ?: 0
        val items = pages.getOrNull(index).orEmpty()
        val next = pages.getOrNull(index + 1)?.let { PageCursor((index + 2).toString()) }
        return FeedPage(
            items = items.map { it.copy(feedId = feedId) },
            nextCursor = next,
            fromCache = false,
            updatedAt = "2026-08-03",
            totalPages = pages.size,
        )
    }

    @Deprecated("Use ArticleContentSource instead")
    override suspend fun getArticle(articleId: String): ArticleContent = error("Use ArticleContentSource")

    override suspend fun getComments(articleId: String): List<CommentNode> = emptyList()
}

/**
 * Paginated Habr hub archive source that throws [HabrRemoteException.RateLimited] on a given
 * page the first [failuresLeft] times it is requested, then succeeds. Used to verify that
 * "load all pages" keeps the paging cursor intact when a page fails.
 */
private class FailingAtPageHubSource(
    private val pages: List<List<FeedItem>>,
    private val failAtPage: Int,
    private var failuresLeft: Int = 1,
) : FeedSource {
    var getItemsCalls: Int = 0
        private set

    override suspend fun getFeeds(): List<FeedDescriptor> = emptyList()

    override suspend fun getItems(feedId: String, page: PageCursor?): FeedPage {
        getItemsCalls += 1
        val pageNumber = page?.value?.toIntOrNull() ?: 1
        if (pageNumber == failAtPage && failuresLeft > 0) {
            failuresLeft -= 1
            throw HabrRemoteException.RateLimited(retryAfterSeconds = 30)
        }
        val index = pageNumber - 1
        val items = pages.getOrNull(index).orEmpty()
        val next = pages.getOrNull(index + 1)?.let { PageCursor((index + 2).toString()) }
        return FeedPage(
            items = items.map { it.copy(feedId = feedId) },
            nextCursor = next,
            fromCache = false,
            updatedAt = "2026-08-03",
            totalPages = pages.size,
        )
    }

    @Deprecated("Use ArticleContentSource instead")
    override suspend fun getArticle(articleId: String): ArticleContent = error("Use ArticleContentSource")

    override suspend fun getComments(articleId: String): List<CommentNode> = emptyList()
}

private fun hubPage(
    feedId: String,
    page: Int,
    count: Int,
): List<FeedItem> = List(count) { index ->
    val globalIndex = (page - 1) * count + index
    FeedItem(
        id = "$feedId:habr-${1000000 + globalIndex}",
        feedId = feedId,
        title = "Article $globalIndex",
        summary = "Summary $globalIndex",
        url = "https://habr.com/ru/articles/${1000000 + globalIndex}/",
        imageUrl = null,
        author = Author("author", "Author", null),
        publishedAt = "2026-08-03",
        publishedAtEpoch = (100 - globalIndex).toLong(),
        tags = listOf(Tag("kotlin", "Kotlin")),
        hubs = listOf(Hub("android_test", "Разработка Android")),
        rating = null,
        commentsCount = null,
        isRead = false,
        isBookmarked = false,
    )
}

private fun mockRssClient(): HttpClient = HttpClient(
    MockEngine { request ->
        assertEquals(
            "https://habr.com/ru/rss/hub/android_dev/all/?fl=ru&with_hubs=true&with_tags=true",
            request.url.toString(),
        )
        respond(
            content = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0">
                    <channel>
                        <item>
                            <title>Fresh RSS Android article</title>
                            <link>https://habr.com/ru/articles/1066224/?utm_source=habrahabr&amp;utm_medium=rss</link>
                            <description><![CDATA[Хабы: Разработка мобильных приложений<br/> Метки: MDM, Android Enterprise<br/> <p>Fresh RSS summary</p>]]></description>
                            <category>Kotlin</category>
                            <pubDate>Mon, 03 Aug 2026 17:19:50 GMT</pubDate>
                            <author>Author</author>
                        </item>
                        <item>
                            <title>RSS duplicate</title>
                            <link>https://habr.com/ru/articles/1066094/?utm_source=habrahabr&amp;utm_medium=rss</link>
                            <description><![CDATA[Хабы: Android<br/> Метки: Kotlin<br/> <p>Duplicate RSS summary</p>]]></description>
                            <category>Kotlin</category>
                            <pubDate>Mon, 03 Aug 2026 12:44:29 GMT</pubDate>
                            <author>Author</author>
                        </item>
                    </channel>
                </rss>
            """.trimIndent(),
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/rss+xml"),
        )
    },
)

private fun remoteItem(
    id: String,
    title: String,
): FeedItem = FeedItem(
    id = id,
    feedId = "feed",
    title = title,
    summary = "Summary $title",
    url = "https://example.com/$id",
    imageUrl = null,
    author = Author("author", "Author", null),
    publishedAt = "2026-05-01",
    publishedAtEpoch = null,
    tags = listOf(Tag("kotlin", "Kotlin")),
    hubs = listOf(Hub("android", "Android")),
    rating = null,
    commentsCount = null,
    isRead = false,
    isBookmarked = false,
)

private fun habrApiItem(
    feedId: String,
    articleId: String,
    title: String,
    publishedAtEpoch: Long,
): FeedItem = FeedItem(
    id = "$feedId:habr-$articleId",
    feedId = feedId,
    title = title,
    summary = "Summary $title",
    url = "https://habr.com/ru/articles/$articleId/",
    imageUrl = null,
    author = Author("author", "Author", null),
    publishedAt = "2026-08-03",
    publishedAtEpoch = publishedAtEpoch,
    tags = listOf(Tag("kotlin", "Kotlin")),
    hubs = listOf(Hub("android_dev", "Разработка мобильных приложений")),
    rating = null,
    commentsCount = null,
    isRead = false,
    isBookmarked = false,
)

private fun relatedItem(
    id: String,
    tags: List<String>,
    hubs: List<String>,
    publishedAtEpoch: Long,
): FeedItem = FeedItem(
    id = id,
    feedId = "feed",
    title = "Article $id",
    summary = "Summary $id",
    url = "https://example.com/$id",
    imageUrl = null,
    author = Author("author", "Author", null),
    publishedAt = "2026-05-01",
    publishedAtEpoch = publishedAtEpoch,
    tags = tags.map { Tag(it, it) },
    hubs = hubs.map { Hub(it, it) },
    rating = null,
    commentsCount = null,
    isRead = false,
    isBookmarked = false,
)

internal class FakeFeedSource : FeedSource {
    val kotlinTag = Tag("kotlin", "Kotlin")
    val composeTag = Tag("compose", "Compose")
    val androidHub = Hub("android", "Android")
    val desktopHub = Hub("desktop", "Desktop")
    private val author = Author("author", "Author", null)

    private val items = listOf(
        FeedItem(
            id = "kotlin",
            feedId = "feed",
            title = "Kotlin RSS",
            summary = "KMP article",
            url = "https://example.com/kotlin",
            imageUrl = "https://example.com/kotlin.jpg",
            author = author,
            publishedAt = "2026-05-01",
            publishedAtEpoch = null,
            tags = listOf(kotlinTag),
            hubs = listOf(androidHub),
            rating = "+1",
            commentsCount = 1,
            isRead = false,
            isBookmarked = false,
        ),
        FeedItem(
            id = "compose",
            feedId = "feed",
            title = "Compose Desktop",
            summary = "Desktop article",
            url = "https://example.com/compose",
            imageUrl = "https://example.com/compose.jpg",
            author = author,
            publishedAt = "2026-05-01",
            publishedAtEpoch = null,
            tags = listOf(composeTag),
            hubs = listOf(desktopHub),
            rating = "+2",
            commentsCount = 2,
            isRead = false,
            isBookmarked = false,
        ),
    )

    override suspend fun getFeeds(): List<FeedDescriptor> = listOf(
        FeedDescriptor(
            id = "feed",
            title = "Feed",
            sourceTitle = "Fake",
            url = "https://example.com/rss",
            description = "Fake feed",
            kind = FeedKind.Custom,
        ),
    )

    override suspend fun getItems(feedId: String, page: PageCursor?): FeedPage {
        return FeedPage(
            items = items,
            nextCursor = null,
            fromCache = false,
            updatedAt = "2026-05-01",
        )
    }

    @Deprecated("Use ArticleContentSource instead", ReplaceWith("FakeArticleContentSource"))
    override suspend fun getArticle(articleId: String): ArticleContent {
        val item = items.first { it.id == articleId || it.url == articleId }
        return ArticleContent(
            id = item.id,
            title = item.title,
            url = item.url,
            imageUrl = item.imageUrl,
            author = item.author,
            publishedAt = item.publishedAt,
            tags = item.tags,
            hubs = item.hubs,
            blocks = listOf(ArticleBlock.Paragraph(listOf(InlineNode.Text("Full ${item.summary} body")))),
            sourceNotice = "Old fallback path",
        )
    }

    override suspend fun getComments(articleId: String): List<CommentNode> = emptyList()
}

/**
 * Fake ArticleContentSource for testing the new separation of concerns.
 */
internal class FakeArticleContentSource : ArticleContentSource {
    override suspend fun getArticleByUrl(url: String): ArticleContent {
        val itemId = url.substringAfterLast("/").substringBefore(".")
        val author = Author("author", "Author", null)
        return ArticleContent(
            id = itemId,
            title = "Full article",
            url = url,
            imageUrl = "https://example.com/$itemId.jpg",
            author = author,
            publishedAt = "2026-05-01",
            tags = listOf(Tag("kotlin", "Kotlin")),
            hubs = listOf(Hub("android", "Android")),
            blocks = listOf(ArticleBlock.Paragraph(listOf(InlineNode.Text("Full KMP article body")))),
            sourceNotice = "Full article from content source.",
        )
    }
}

internal class MutableFakeArticleContentSource : ArticleContentSource {
    var fail: Boolean = false

    override suspend fun getArticleByUrl(url: String): ArticleContent {
        if (fail) error("Network is unavailable")
        return FakeArticleContentSource().getArticleByUrl(url)
    }
}


private fun List<InlineNode>.plain(): String = joinToString("") { node ->
    when (node) {
        is InlineNode.Bold -> node.children.plain()
        is InlineNode.Code -> node.value
        is InlineNode.Italic -> node.children.plain()
        is InlineNode.Link -> node.text
        is InlineNode.Text -> node.value
    }
}
