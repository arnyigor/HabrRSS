package com.arny.habrrss

import com.arny.habrrss.data.database.ArticleLocalStateEntity
import com.arny.habrrss.data.database.FavoriteArticleEntity
import com.arny.habrrss.data.database.FeedItemEntity
import com.arny.habrrss.data.database.InMemoryFeedDao
import kotlinx.coroutines.flow.first
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

    @Test
    fun getAllCachedPagedAppliesFiltersLimitsAndOffset() = runTest {
        val dao = InMemoryFeedDao()
        dao.insertAll(
            listOf(
                pagedEntity(id = "a", title = "Kotlin Flow", hubsJson = ANDROID_HUB, tagsJson = KOTLIN_TAG, publishedAtEpoch = 3),
                pagedEntity(id = "b", title = "Compose UI", hubsJson = DESKTOP_HUB, tagsJson = COMPOSE_TAG, publishedAtEpoch = 2),
                pagedEntity(id = "c", title = "Kotlin Coroutines", hubsJson = ANDROID_HUB, tagsJson = KOTLIN_TAG, publishedAtEpoch = 1),
                pagedEntity(id = "d", title = "Python Async", hubsJson = PYTHON_HUB, tagsJson = PYTHON_TAG, publishedAtEpoch = 0),
            ),
        )

        // Newest first ordering + limit/offset slicing.
        assertEquals(listOf("a", "b"), dao.getAllCachedPaged(null, null, null, false, limit = 2, offset = 0).first().map { it.id })
        assertEquals(listOf("c", "d"), dao.getAllCachedPaged(null, null, null, false, limit = 2, offset = 2).first().map { it.id })
        assertEquals(4, dao.countAllCachedPaged(null, null, null, false))

        // Hub filter.
        assertEquals(listOf("a", "c"), dao.getAllCachedPaged(hubFilter = "android", null, null, false, limit = 10, offset = 0).first().map { it.id })
        assertEquals(2, dao.countAllCachedPaged(hubFilter = "android", null, null, false))

        // Tag filter.
        assertEquals(listOf("b"), dao.getAllCachedPaged(null, tagFilter = "compose", null, false, limit = 10, offset = 0).first().map { it.id })

        // Text query against title.
        assertEquals(listOf("a"), dao.getAllCachedPaged(null, null, query = "Flow", false, limit = 10, offset = 0).first().map { it.id })

        // hideRead excludes rows with isRead = 1 in article_local_state.
        dao.upsertArticleLocalState(ArticleLocalStateEntity(articleId = "a", isRead = true))
        assertEquals(listOf("b", "c", "d"), dao.getAllCachedPaged(null, null, null, hideRead = true, limit = 10, offset = 0).first().map { it.id })
        assertEquals(3, dao.countAllCachedPaged(null, null, null, hideRead = true))
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

    private fun pagedEntity(
        id: String,
        title: String,
        hubsJson: String,
        tagsJson: String,
        publishedAtEpoch: Long,
    ): FeedItemEntity = FeedItemEntity(
        id = id,
        feedId = "feed",
        title = title,
        summary = "summary $title",
        descriptionHtml = null,
        url = "https://example.com/$id",
        imageUrl = null,
        authorName = "Author",
        authorProfileUrl = null,
        publishedAt = "2026-05-01",
        publishedAtEpoch = publishedAtEpoch,
        tagsJson = tagsJson,
        hubsJson = hubsJson,
        rating = null,
        commentsCount = 0,
        fetchedAt = publishedAtEpoch,
    )

    private companion object {
        const val ANDROID_HUB = """[{"id":"android","title":"Android"}]"""
        const val DESKTOP_HUB = """[{"id":"desktop","title":"Desktop"}]"""
        const val PYTHON_HUB = """[{"id":"python","title":"Python"}]"""
        const val KOTLIN_TAG = """[{"id":"kotlin","title":"Kotlin"}]"""
        const val COMPOSE_TAG = """[{"id":"compose","title":"Compose"}]"""
        const val PYTHON_TAG = """[{"id":"python","title":"Python"}]"""
    }
}
