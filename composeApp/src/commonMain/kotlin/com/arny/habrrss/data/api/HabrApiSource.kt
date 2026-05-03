package com.arny.habrrss.data.api

import com.arny.habrrss.domain.models.ArticleContent
import com.arny.habrrss.domain.models.CommentNode
import com.arny.habrrss.domain.models.FeedDescriptor
import com.arny.habrrss.domain.models.FeedPage
import com.arny.habrrss.domain.models.PageCursor
import com.arny.habrrss.domain.source.FeedSource
import com.arny.habrrss.domain.source.SourceUnavailableException

class HabrApiSource : FeedSource {
    override suspend fun getFeeds(): List<FeedDescriptor> = emptyList()

    override suspend fun getItems(feedId: String, page: PageCursor?): FeedPage {
        throw SourceUnavailableException("Habr API is intentionally not used as the MVP data source.")
    }

    @Deprecated("Use ArticleContentSource instead", ReplaceWith("ArticleContentSource"))
    override suspend fun getArticle(articleId: String): ArticleContent {
        throw SourceUnavailableException("Habr API adapter is a future extension point.")
    }

    override suspend fun getComments(articleId: String): List<CommentNode> = emptyList()
}
