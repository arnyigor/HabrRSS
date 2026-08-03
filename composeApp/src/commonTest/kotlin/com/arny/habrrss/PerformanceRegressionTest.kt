package com.arny.habrrss

import com.arny.habrrss.data.database.InMemoryFeedDao
import com.arny.habrrss.data.repository.TechReaderRepository
import com.arny.habrrss.domain.models.ArticleContent
import com.arny.habrrss.domain.models.Author
import com.arny.habrrss.domain.models.CommentNode
import com.arny.habrrss.domain.models.FeedDescriptor
import com.arny.habrrss.domain.models.FeedItem
import com.arny.habrrss.domain.models.FeedKind
import com.arny.habrrss.domain.models.FeedPage
import com.arny.habrrss.domain.models.Hub
import com.arny.habrrss.domain.models.PageCursor
import com.arny.habrrss.domain.models.Tag
import com.arny.habrrss.domain.source.FeedSource
import com.arny.habrrss.domain.usecases.GetFeedsUseCase
import com.arny.habrrss.domain.usecases.HasMorePagesUseCase
import com.arny.habrrss.domain.usecases.LoadNextPageUseCase
import com.arny.habrrss.domain.usecases.OpenArticleUseCase
import com.arny.habrrss.domain.usecases.RefreshFeedUseCase
import com.arny.habrrss.domain.usecases.ToggleBookmarkUseCase
import com.arny.habrrss.presentation.ReaderInteractor
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

class PerformanceRegressionTest {
    @Test
    fun startLoadsOnlyFirstPageAndKeepsNewestOrder() = runTest {
        val source = PagedCountingFeedSource(totalItems = 60, pageSize = 20)
        val presenter = createPresenter(source)

        presenter.start()

        val state = presenter.state.value
        assertEquals(listOf(1), source.requestedPages)
        assertEquals(1, source.getFeedsCalls)
        assertEquals(20, state.items.size)
        assertEquals("article-00000", state.items.first().id)
        assertEquals("article-00019", state.items.last().id)
        assertTrue(state.canLoadMore)
        assertFalse(state.isArticleOpen)
    }

    @Test
    fun localFiltersDoNotTriggerRemoteFetchesAfterStart() = runTest {
        val source = PagedCountingFeedSource(totalItems = 200, pageSize = 50)
        val presenter = createPresenter(source)

        presenter.start()
        val requestsAfterStart = source.requestedPages.toList()

        presenter.updateSearchQuery("Article 0001")
        assertEquals(
            listOf(
                "article-00001",
                "article-00010",
                "article-00011",
                "article-00012",
                "article-00013",
                "article-00014",
                "article-00015",
                "article-00016",
                "article-00017",
                "article-00018",
                "article-00019",
            ),
            presenter.state.value.visibleItems.map { it.id },
        )

        presenter.selectHub("android")
        presenter.selectTag("kotlin")
        presenter.clearFilters()

        assertEquals(requestsAfterStart, source.requestedPages)
        assertEquals(50, presenter.state.value.visibleItems.size)
    }

    @Test
    fun loadMoreFetchesNextPagesOnlyAndStopsAtEnd() = runTest {
        val source = PagedCountingFeedSource(totalItems = 45, pageSize = 20)
        val presenter = createPresenter(source)

        presenter.start()

        assertTrue(presenter.loadMoreItems())
        assertEquals(listOf(1, 2), source.requestedPages)
        assertEquals(40, presenter.state.value.items.size)
        assertTrue(presenter.state.value.canLoadMore)

        assertTrue(presenter.loadMoreItems())
        assertEquals(listOf(1, 2, 3), source.requestedPages)
        assertEquals(45, presenter.state.value.items.size)
        assertFalse(presenter.state.value.canLoadMore)

        assertFalse(presenter.loadMoreItems())
        assertEquals(listOf(1, 2, 3), source.requestedPages)
    }

    @Test
    fun largeFeedRefreshAndUnchangedRefreshStayWithinGuardrail() = runTest {
        val source = PagedCountingFeedSource(totalItems = LARGE_FEED_SIZE, pageSize = LARGE_FEED_SIZE)
        val repository = TechReaderRepository(
            primarySource = source,
            feedDao = InMemoryFeedDao(),
        )

        repository.getFeeds(forceRefresh = true)
        val firstRefresh = measureTime {
            val page = repository.refreshFeed(FEED_ID, force = true)
            assertEquals(LARGE_FEED_SIZE, page.items.size)
        }
        val unchangedRefresh = measureTime {
            val page = repository.refreshFeed(FEED_ID, force = true)
            assertEquals(LARGE_FEED_SIZE, page.items.size)
        }
        val cached = repository.getCachedFeed(FEED_ID)

        assertEquals(2, source.requestedPages.size)
        assertEquals(LARGE_FEED_SIZE, cached.size)
        assertEquals("article-00000", cached.first().id)
        assertEquals("article-09999", cached.last().id)
        assertWithinGuardrail("first refresh", firstRefresh, LARGE_REFRESH_BUDGET)
        assertWithinGuardrail("unchanged refresh", unchangedRefresh, LARGE_UNCHANGED_REFRESH_BUDGET)
    }

    @Test
    fun openingArticleAfterLargeRefreshMarksReadWithoutRefetchingFeed() = runTest {
        val source = PagedCountingFeedSource(totalItems = LARGE_FEED_SIZE, pageSize = LARGE_FEED_SIZE)
        val repository = TechReaderRepository(
            primarySource = source,
            feedDao = InMemoryFeedDao(),
            articleContentSource = FakeArticleContentSource(),
        )

        repository.getFeeds(forceRefresh = true)
        repository.refreshFeed(FEED_ID, force = true)
        val requestsAfterRefresh = source.requestedPages.toList()
        val openElapsed = measureTime {
            val article = repository.getArticle("article-05000")
            assertEquals("article-05000", article.id)
        }
        val cachedArticle = repository.getCachedFeed(FEED_ID).first { it.id == "article-05000" }

        assertEquals(requestsAfterRefresh, source.requestedPages)
        assertTrue(cachedArticle.isRead)
        assertWithinGuardrail("open article", openElapsed, OPEN_ARTICLE_BUDGET)
    }

    private fun createPresenter(source: PagedCountingFeedSource): ReaderInteractor {
        val repository = TechReaderRepository(
            primarySource = source,
            feedDao = InMemoryFeedDao(),
            articleContentSource = FakeArticleContentSource(),
        )
        return ReaderInteractor(
            repository = repository,
            getFeeds = GetFeedsUseCase(repository),
            refreshFeed = RefreshFeedUseCase(repository),
            openArticle = OpenArticleUseCase(repository),
            toggleBookmark = ToggleBookmarkUseCase(repository),
            loadNextPage = LoadNextPageUseCase(repository),
            hasMorePages = HasMorePagesUseCase(repository),
        )
    }

    private fun assertWithinGuardrail(operation: String, actual: Duration, budget: Duration) {
        assertTrue(
            actual < budget,
            "$operation took $actual, expected below $budget on in-memory fake data",
        )
    }
}

private class PagedCountingFeedSource(
    private val totalItems: Int,
    private val pageSize: Int,
) : FeedSource {
    var getFeedsCalls: Int = 0
        private set
    val requestedPages: MutableList<Int> = mutableListOf()

    override suspend fun getFeeds(): List<FeedDescriptor> {
        getFeedsCalls += 1
        return listOf(
            FeedDescriptor(
                id = FEED_ID,
                title = "Performance feed",
                sourceTitle = "Fake",
                url = "https://example.com/rss",
                description = "Deterministic performance feed",
                kind = FeedKind.Custom,
            ),
        )
    }

    override suspend fun getItems(feedId: String, page: PageCursor?): FeedPage {
        val pageNumber = page?.value?.toIntOrNull() ?: 1
        requestedPages += pageNumber
        val start = (pageNumber - 1) * pageSize
        val items = (start until (start + pageSize).coerceAtMost(totalItems))
            .map { index -> performanceItem(index, totalItems, feedId) }
        val nextPage = pageNumber + 1
        return FeedPage(
            items = items,
            nextCursor = if (nextPage * pageSize - pageSize < totalItems) PageCursor(nextPage.toString()) else null,
            fromCache = false,
            updatedAt = "2026-08-03",
        )
    }

    @Deprecated("Use ArticleContentSource instead")
    override suspend fun getArticle(articleId: String): ArticleContent = error("Use ArticleContentSource")

    override suspend fun getComments(articleId: String): List<CommentNode> = emptyList()
}

private fun performanceItem(index: Int, totalItems: Int, feedId: String): FeedItem = FeedItem(
    id = "article-${index.toString().padStart(5, '0')}",
    feedId = feedId,
    title = "Article ${index.toString().padStart(5, '0')}",
    summary = "Kotlin Compose performance sample $index",
    url = "https://example.com/article-${index.toString().padStart(5, '0')}",
    imageUrl = null,
    author = Author("author", "Author", null),
    publishedAt = "2026-08-03",
    publishedAtEpoch = (totalItems - index).toLong(),
    tags = listOf(Tag("kotlin", "Kotlin")),
    hubs = listOf(Hub("android", "Android")),
    rating = "+$index",
    commentsCount = index,
    isRead = false,
    isBookmarked = false,
)

private const val FEED_ID = "feed"
private const val LARGE_FEED_SIZE = 10_000
private val LARGE_REFRESH_BUDGET = 10.seconds
private val LARGE_UNCHANGED_REFRESH_BUDGET = 10.seconds
private val OPEN_ARTICLE_BUDGET = 5.seconds
