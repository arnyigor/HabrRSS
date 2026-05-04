package com.arny.habrrss

import com.arny.habrrss.domain.models.ArticleBlock
import com.arny.habrrss.domain.models.ArticleContent
import com.arny.habrrss.domain.models.Author
import com.arny.habrrss.domain.models.FeedDescriptor
import com.arny.habrrss.domain.models.FeedItem
import com.arny.habrrss.domain.models.FeedKind
import com.arny.habrrss.domain.models.Hub
import com.arny.habrrss.domain.models.InlineNode
import com.arny.habrrss.domain.models.Tag
import com.arny.habrrss.presentation.ReaderUiState
import com.arny.habrrss.presentation.feed.HabrPublicationSection
import com.arny.habrrss.presentation.feed.estimatedReadingMinutes
import com.arny.habrrss.presentation.feed.habrCommentsLabel
import com.arny.habrrss.presentation.feed.habrFeedTabs
import com.arny.habrrss.presentation.feed.habrScoreLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HabrFeedPresentationTest {
    @Test
    fun habrFeedTabsSelectActiveFeedAndEnableOnlyAvailableSections() {
        val state = ReaderUiState(
            feeds = listOf(
                feed("habr-all", FeedKind.All),
                feed("habr-posts", FeedKind.Posts),
                feed("habr-news", FeedKind.News),
            ),
            activeFeedId = "habr-all",
            items = listOf(item("one"), item("two")),
            visibleItems = listOf(item("one"), item("two")),
        )

        val tabs = state.habrFeedTabs()

        val articles = tabs.first { it.section == HabrPublicationSection.Articles }
        val posts = tabs.first { it.section == HabrPublicationSection.Posts }
        val hubs = tabs.first { it.section == HabrPublicationSection.Hubs }

        assertEquals(
            listOf(
                HabrPublicationSection.Articles,
                HabrPublicationSection.Posts,
                HabrPublicationSection.News,
                HabrPublicationSection.Hubs,
            ),
            tabs.map { it.section },
        )
        assertTrue(articles.selected)
        assertTrue(articles.enabled)
        assertEquals(2, articles.count)
        assertFalse(posts.selected)
        assertTrue(posts.enabled)
        assertTrue(hubs.enabled)
    }

    @Test
    fun feedItemReadingTimeUsesSummaryAndHtmlDescription() {
        val text = (1..181).joinToString(" ") { "word" }
        val item = item("long").copy(summary = "", descriptionHtml = "<p>$text</p>")

        assertEquals(2, item.estimatedReadingMinutes())
    }

    @Test
    fun articleReadingTimeUsesBlocks() {
        val text = (1..180).joinToString(" ") { "word" }
        val article = article(
            blocks = listOf(ArticleBlock.Paragraph(listOf(InlineNode.Text(text)))),
        )

        assertEquals(1, article.estimatedReadingMinutes())
    }

    @Test
    fun scoreAndCommentsFallbacksMatchHabrLikeCard() {
        val item = item("stats").copy(rating = null, commentsCount = null)

        assertEquals("0", item.habrScoreLabel())
        assertEquals("0", item.habrCommentsLabel())
    }

    private fun feed(id: String, kind: FeedKind): FeedDescriptor = FeedDescriptor(
        id = id,
        title = id,
        sourceTitle = "Habr",
        url = "https://example.com/$id.xml",
        description = id,
        kind = kind,
    )

    private fun item(id: String): FeedItem = FeedItem(
        id = id,
        feedId = "habr-all",
        title = "Title $id",
        summary = "Short publication summary",
        descriptionHtml = null,
        url = "https://habr.com/ru/articles/$id/",
        imageUrl = null,
        author = Author("author", "Author", null),
        publishedAt = "2026-05-02",
        publishedAtEpoch = null,
        tags = listOf(Tag("tag", "Tag")),
        hubs = listOf(Hub("hub", "Hub")),
        rating = "+1",
        commentsCount = 1,
        isRead = false,
        isBookmarked = false,
    )

    private fun article(blocks: List<ArticleBlock>): ArticleContent = ArticleContent(
        id = "article",
        title = "Article",
        url = "https://habr.com/ru/articles/1/",
        imageUrl = null,
        author = Author("author", "Author", null),
        publishedAt = "2026-05-02",
        tags = emptyList(),
        hubs = emptyList(),
        blocks = blocks,
        sourceNotice = "RSS",
    )
}
