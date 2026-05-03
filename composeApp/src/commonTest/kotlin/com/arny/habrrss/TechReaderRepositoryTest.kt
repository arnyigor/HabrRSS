package com.arny.habrrss

import com.arny.habrrss.data.database.InMemoryFeedDao
import com.arny.habrrss.data.repository.TechReaderRepository
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
