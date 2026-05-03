package com.arny.habrrss.data.article

import com.arny.habrrss.domain.models.ArticleBlock
import com.arny.habrrss.domain.models.ArticleContent
import com.arny.habrrss.domain.models.InlineNode
import com.arny.habrrss.domain.source.ArticleContentSource

class HabrWebReaderSource : ArticleContentSource {
    override suspend fun getArticleByUrl(url: String): ArticleContent {
        return ArticleContent(
            id = url,
            title = "Open original article",
            url = url,
            imageUrl = null,
            author = null,
            publishedAt = null,
            tags = emptyList(),
            hubs = emptyList(),
            sourceNotice = "HTML reader mode is a fallback, not the primary feed source.",
            blocks = listOf(
                ArticleBlock.Paragraph(
                    listOf(
                        InlineNode.Text("Reader mode fallback is reserved for articles where RSS content is incomplete or too complex to normalize safely."),
                    ),
                ),
            ),
        )
    }
}
