package com.arny.habrrss

import com.arny.habrrss.data.database.InMemoryFeedDao
import com.arny.habrrss.data.repository.TechReaderRepository
import com.arny.habrrss.domain.usecases.GetFeedsUseCase
import com.arny.habrrss.domain.usecases.OpenArticleUseCase
import com.arny.habrrss.domain.usecases.RefreshFeedUseCase
import com.arny.habrrss.domain.usecases.ToggleBookmarkUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FeedUseCasesTest {
    @Test
    fun useCasesDelegateToRepository() = runTest {
        val repository = TechReaderRepository(FakeFeedSource(), InMemoryFeedDao(), FakeArticleContentSource())

        val feeds = GetFeedsUseCase(repository)()
        val page = RefreshFeedUseCase(repository)("feed")
        val article = OpenArticleUseCase(repository)("kotlin")
        ToggleBookmarkUseCase(repository)("kotlin")
        val cached = repository.getCachedFeed("feed").first { it.id == "kotlin" }

        assertEquals("feed", feeds.single().id)
        assertEquals(2, page.items.size)
        assertEquals("Full article from content source.", article.sourceNotice)
        assertTrue(cached.isBookmarked)
    }
}
