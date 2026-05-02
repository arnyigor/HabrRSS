package com.arny.habrrss.domain.source

import com.arny.habrrss.domain.models.ArticleContent
import com.arny.habrrss.domain.models.CommentNode
import com.arny.habrrss.domain.models.FeedDescriptor
import com.arny.habrrss.domain.models.FeedPage
import com.arny.habrrss.domain.models.PageCursor

interface FeedSource {
    suspend fun getFeeds(): List<FeedDescriptor>
    suspend fun getItems(feedId: String, page: PageCursor?): FeedPage
    suspend fun getArticle(articleId: String): ArticleContent
    suspend fun getComments(articleId: String): List<CommentNode>
}

interface ArticleContentSource {
    suspend fun getArticle(articleUrl: String): ArticleContent
}

class SourceUnavailableException(message: String) : RuntimeException(message)
