package com.arny.habrrss

import com.arny.habrrss.domain.models.Bookmark
import com.arny.habrrss.domain.models.CachePolicy
import com.arny.habrrss.domain.models.CommentNode
import com.arny.habrrss.domain.models.CursorDirection
import com.arny.habrrss.domain.models.ExportFormat
import com.arny.habrrss.domain.models.ExportRequest
import com.arny.habrrss.domain.models.PageCursor
import com.arny.habrrss.domain.models.ReadingState
import com.arny.habrrss.domain.models.ThemeMode
import com.arny.habrrss.domain.models.UserFilter
import com.arny.habrrss.domain.source.ArticleRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DomainModelsTest {
    @Test
    fun valueModelsKeepExpectedFields() {
        val cursor = PageCursor("page-2", CursorDirection.Previous)
        val reading = ReadingState("article", isRead = true, lastOpenedAt = "2026-05-02", scrollPosition = 42)
        val bookmark = Bookmark("article", createdAt = "2026-05-02", note = "note")
        val filter = UserFilter(
            hiddenTags = setOf("spam"),
            hiddenAuthors = setOf("bot"),
            hiddenKeywords = setOf("promo"),
            showUnreadOnly = true,
        )
        val export = ExportRequest("article", ExportFormat.Pdf, includeImages = false, targetPath = "/tmp/out.pdf")
        val comment = CommentNode("comment", author = null, publishedAt = null, body = emptyList(), children = emptyList())

        assertEquals("page-2", cursor.value)
        assertEquals(CursorDirection.Previous, cursor.direction)
        assertTrue(reading.isRead)
        assertEquals(42, reading.scrollPosition)
        assertEquals("note", bookmark.note)
        assertTrue(filter.hiddenTags.contains("spam"))
        assertEquals(ExportFormat.Pdf, export.format)
        assertFalse(export.includeImages)
        assertEquals("comment", comment.id)
    }

    @Test
    fun defaultsAndArticleRefsAreStable() {
        val settings = com.arny.habrrss.domain.models.FeedSettings.defaults()

        assertEquals(ThemeMode.System, settings.themeMode)
        assertEquals(CachePolicy.OnlineFirst, settings.offlinePolicy)
        assertEquals("123", ArticleRef.ById("123").value)
        assertEquals("https://example.com", ArticleRef.ByUrl("https://example.com").value)
    }
}
