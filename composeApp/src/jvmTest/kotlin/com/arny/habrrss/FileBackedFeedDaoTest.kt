package com.arny.habrrss

import com.arny.habrrss.data.database.FavoriteArticleEntity
import com.arny.habrrss.data.database.FeedItemEntity
import com.arny.habrrss.data.database.FileBackedFeedDao
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class FileBackedFeedDaoTest {
    @Test
    fun persistsFeedItemsBetweenDaoInstances() = runTest {
        val file = File.createTempFile("habr-feed-cache", ".json")
        file.deleteOnExit()
        val firstDao = FileBackedFeedDao(file)

        firstDao.insertAll(
            listOf(
                FeedItemEntity(
                    id = "article",
                    feedId = "feed",
                    title = "Cached article",
                    summary = "Summary",
                    descriptionHtml = "<p>Summary</p>",
                    url = "https://example.com/article",
                    imageUrl = null,
                    authorName = "Author",
                    authorProfileUrl = null,
                    publishedAt = "2026-05-02",
                    publishedAtEpoch = null,
                    tagsJson = "[]",
                    hubsJson = "[]",
                    rating = "+1",
                    commentsCount = 1,
                    cachedArticleJson = """{"id":"article"}""",
                    fetchedAt = 1L,
                ),
            ),
        )
        firstDao.insertFavoriteArticle(FavoriteArticleEntity(articleId = "article", createdAt = 1L))

        val secondDao = FileBackedFeedDao(file)
        val cached = secondDao.getByFeedOnce("feed").single()
        val bookmark = secondDao.getBookmarksOnce().single()

        assertEquals("Cached article", cached.title)
        assertEquals("""{"id":"article"}""", cached.cachedArticleJson)
        assertEquals("article", bookmark.id)
    }
}
