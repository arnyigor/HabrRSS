package com.arny.habrrss.domain.usecases

import com.arny.habrrss.data.repository.TechReaderRepository
import com.arny.habrrss.domain.models.ArticleContent
import com.arny.habrrss.domain.models.FeedDescriptor
import com.arny.habrrss.domain.models.FeedItem
import com.arny.habrrss.domain.models.FeedPage

class GetFeedsUseCase(private val repository: TechReaderRepository) {
    suspend operator fun invoke(): List<FeedDescriptor> = repository.getFeeds()
}

class RefreshFeedUseCase(private val repository: TechReaderRepository) {
    suspend operator fun invoke(feedId: String): FeedPage = repository.refreshFeed(feedId)
}

class OpenArticleUseCase(private val repository: TechReaderRepository) {
    suspend operator fun invoke(articleId: String): ArticleContent = repository.getArticle(articleId)
}

class ToggleBookmarkUseCase(private val repository: TechReaderRepository) {
    suspend operator fun invoke(articleId: String) = repository.toggleBookmark(articleId)
}