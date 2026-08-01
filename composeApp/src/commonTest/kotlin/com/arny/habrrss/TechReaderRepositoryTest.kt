package com.arny.habrrss

import com.arny.habrrss.data.database.InMemoryFeedDao
import com.arny.habrrss.data.preferences.DefaultPreferencesRepository
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
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
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
        repository.refreshFeed("feed")

        assertEquals("New title", dao.getById("one")?.title)
        assertNotNull(dao.getById("stale"))
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

        assertTrue(repository.getFeeds(forceRefresh = true).any { it.url.contains("/rss/hub/android/") })

        repository.toggleFavoriteHub(hubId = "android", title = "Android")

        assertFalse(repository.getFeeds(forceRefresh = true).any { it.url.contains("/rss/hub/android/") })
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
        val customFeed = repository.getFeeds(forceRefresh = true).first { it.id.startsWith("custom-hub-") }

        assertEquals("android", customFeed.title)
        assertEquals(
            "https://habr.com/ru/rss/hub/android/?limit=100&with_hubs=true&with_tags=true",
            customFeed.url,
        )
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
) : FeedSource {
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

    override suspend fun getItems(feedId: String, page: PageCursor?): FeedPage = FeedPage(
        items = items.map { it.copy(feedId = feedId) },
        nextCursor = null,
        fromCache = false,
        updatedAt = "2026-05-01",
    )

    @Deprecated("Use ArticleContentSource instead")
    override suspend fun getArticle(articleId: String): ArticleContent = error("Use ArticleContentSource")

    override suspend fun getComments(articleId: String): List<CommentNode> = emptyList()
}

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
