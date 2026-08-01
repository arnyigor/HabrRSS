package com.arny.habrrss

import com.arny.habrrss.core.network.createHttpClient
import com.arny.habrrss.data.article.HabrArticleContentSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class CommentsApiTest {

    @Test
    fun loadsCommentsFromHabrApi() = runTest {
        val client = createHttpClient()
        try {
            val source = HabrArticleContentSource(client)
            // Use a known article with comments
            val url = "https://habr.com/ru/articles/887318/"
            println("[TEST] Calling getCommentsByUrl($url)")
            val comments = source.getCommentsByUrl(url)
            println("[TEST] Got ${comments.size} root comments")
            comments.forEach { comment ->
                println("[TEST]   - ${comment.author?.displayName}: ${comment.body.take(50)}")
                println("[TEST]     children: ${comment.children.size}")
            }
            assertTrue(comments.isNotEmpty(), "Expected comments from Habr API, got 0")
        } finally {
            client.close()
        }
    }

    @Test
    fun extractsArticleIdFromUrl() = runTest {
        val source = HabrArticleContentSource(createHttpClient())
        // Test the URL format
        val url = "https://habr.com/ru/articles/887318/"
        val comments = source.getCommentsByUrl(url)
        println("[TEST] Article ID extraction test: got ${comments.size} comments from $url")
        assertTrue(comments.isNotEmpty(), "Expected comments for article 887318")
    }
}
