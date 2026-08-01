package com.arny.habrrss

import com.arny.habrrss.data.database.ArticleLocalStateEntity
import com.arny.habrrss.data.database.FavoriteArticleEntity
import com.arny.habrrss.data.database.FeedItemEntity
import com.arny.habrrss.data.database.InMemoryFeedDao
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InMemoryFeedDaoTest {
    @Test
    fun insertSearchUpdateBookmarkAndDeleteWorkTogether() = runTest {
        val dao = InMemoryFeedDao()
        dao.insertAll(
            listOf(
                entity(id = "one", title = "Kotlin Flow", fetchedAt = 1),
                entity(id = "two", title = "Compose UI", fetchedAt = 2),
            ),
        )

        assertEquals(listOf("two", "one"), dao.getByFeedOnce("feed").map { it.id })
        assertEquals(listOf("one"), dao.search("Kotlin").map { it.id })

        dao.upsertArticleLocalState(ArticleLocalStateEntity(articleId = "one", isRead = true))
        dao.insertFavoriteArticle(FavoriteArticleEntity(articleId = "one", createdAt = 1L))

        val updated = dao.getArticleLocalState("one")
        assertEquals(true, updated?.isRead)
        assertEquals(listOf("one"), dao.getBookmarksOnce().map { it.id })

        dao.deleteOldByFeed("feed", timestamp = 2)
        assertNull(dao.getById("one"))
        assertEquals(listOf("two"), dao.getByFeedOnce("feed").map { it.id })

        dao.deleteAll()
        assertTrue(dao.getByFeedOnce("feed").isEmpty())
    }

    @Test
    fun insertAllReplacesExistingEntityById() = runTest {
        val dao = InMemoryFeedDao()

        dao.insertAll(listOf(entity(id = "one", title = "Old")))
        dao.insertAll(listOf(entity(id = "one", title = "New")))

        assertEquals("New", dao.getById("one")?.title)
        assertEquals(1, dao.getByFeedOnce("feed").size)
    }

    private fun entity(
        id: String,
        title: String,
        fetchedAt: Long = 1L,
    ): FeedItemEntity = FeedItemEntity(
        id = id,
        feedId = "feed",
        title = title,
        summary = "summary $title",
        descriptionHtml = "<p>$title</p>",
        url = "https://example.com/$id",
        imageUrl = null,
        authorName = "Author",
        authorProfileUrl = null,
        publishedAt = "2026-05-0$fetchedAt",
        publishedAtEpoch = null,
        tagsJson = "[]",
        hubsJson = "[]",
        rating = "+1",
        commentsCount = 0,
        fetchedAt = fetchedAt,
    )
}
