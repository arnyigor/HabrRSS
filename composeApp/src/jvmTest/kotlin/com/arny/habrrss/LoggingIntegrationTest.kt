package com.arny.habrrss

import com.arny.habrrss.core.logging.AppLog
import com.arny.habrrss.data.database.InMemoryFeedDao
import com.arny.habrrss.data.repository.TechReaderRepository
import com.arny.habrrss.presentation.ArticleViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertTrue

class LoggingIntegrationTest {
    @Test
    fun appLogIncludesCallingThreadNameOnJvm() = runTest {
        val output = captureStdout {
            val thread = Thread(
                { AppLog.i("ThreadCheck", "operation marker") },
                "habr-log-test-thread",
            )
            thread.start()
            thread.join()
        }

        assertTrue(
            output.contains("I/HabrRSS.ThreadCheck: [thread=habr-log-test-thread] operation marker"),
            output,
        )
    }

    @Test
    fun repositoryOperationsEmitThreadedPerformanceLogs() = runTest {
        val repository = TechReaderRepository(
            primarySource = FakeFeedSource(),
            feedDao = InMemoryFeedDao(),
            articleContentSource = FakeArticleContentSource(),
        )

        val output = captureStdout {
            repository.getFeeds(forceRefresh = true)
            repository.refreshFeed("feed", force = true)
            repository.getArticle("kotlin")
        }

        assertTrue(output.contains("HabrRSS.Repository"), output)
        assertTrue(output.contains("[thread="), output)
        assertTrue(output.contains("getFeeds rebuilding force=true"), output)
        assertTrue(output.contains("loadFeedPage primary=FakeFeedSource feedId=feed page=1"), output)
        assertTrue(output.contains("loadFeedPage primary success source=FakeFeedSource feedId=feed items=2"), output)
        assertTrue(output.contains("refreshFeed remote feedId=feed pageItems=2 changed=2"), output)
        assertTrue(output.contains("getArticle loaded articleId=kotlin blocks=1"), output)
        assertTrue(output.contains("elapsed="), output)
    }

    @Test
    fun articleViewModelLoadsArticleOffMainThread() = runTest {
        val repository = TechReaderRepository(
            primarySource = FakeFeedSource(),
            feedDao = InMemoryFeedDao(),
            articleContentSource = FakeArticleContentSource(),
        )
        repository.getFeeds(forceRefresh = true)
        repository.refreshFeed("feed", force = true)
        val viewModel = ArticleViewModel(repository)

        val output = captureStdout {
            viewModel.openArticle("kotlin")
            withTimeout(5_000) {
                while (viewModel.state.value.article == null && viewModel.state.value.errorMessage == null) {
                    delay(10)
                }
            }
        }

        assertTrue(output.contains("HabrRSS.Repository"), output)
        assertTrue(output.contains("getArticle start articleId=kotlin"), output)
        assertTrue(!output.contains("HabrRSS.Repository: [thread=main]"), output)
    }
}

private val stdoutCaptureMutex = Mutex()

private suspend fun captureStdout(block: suspend () -> Unit): String = stdoutCaptureMutex.withLock {
    val original = System.out
    val buffer = ByteArrayOutputStream()
    val replacement = PrintStream(buffer, true, "UTF-8")
    System.setOut(replacement)
    try {
        block()
        replacement.flush()
        buffer.toString("UTF-8")
    } finally {
        System.setOut(original)
        replacement.close()
    }
}
