package com.arny.habrrss.data.rss

import com.arny.habrrss.domain.models.ArticleContent
import com.arny.habrrss.domain.models.CommentNode
import com.arny.habrrss.domain.models.FeedDescriptor
import com.arny.habrrss.domain.models.FeedPage
import com.arny.habrrss.domain.models.PageCursor
import com.arny.habrrss.domain.source.FeedSource
import com.arny.habrrss.domain.source.SourceUnavailableException

class GenericRssSource(
    private val descriptors: List<FeedDescriptor> = emptyList(),
) : FeedSource {
    override suspend fun getFeeds(): List<FeedDescriptor> = descriptors

    override suspend fun getItems(feedId: String, page: PageCursor?): FeedPage {
        return FeedPage(
            items = emptyList(),
            nextCursor = null,
            fromCache = true,
            updatedAt = null,
        )
    }

    @Deprecated("Use ArticleContentSource instead", ReplaceWith("ArticleContentSource"))
    override suspend fun getArticle(articleId: String): ArticleContent {
        throw SourceUnavailableException("Generic RSS article body loading is not implemented yet.")
    }

    override suspend fun getComments(articleId: String): List<CommentNode> = emptyList()
}
