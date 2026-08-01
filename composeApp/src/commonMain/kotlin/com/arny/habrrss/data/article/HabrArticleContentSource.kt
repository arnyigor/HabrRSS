package com.arny.habrrss.data.article

import com.arny.habrrss.data.rss.HtmlArticleParser
import com.arny.habrrss.domain.models.ArticleBlock
import com.arny.habrrss.domain.models.ArticleContent
import com.arny.habrrss.domain.models.Author
import com.arny.habrrss.domain.models.CommentNode
import com.arny.habrrss.domain.source.ArticleCommentsSource
import com.arny.habrrss.domain.source.ArticleContentSource
import com.arny.habrrss.domain.util.toEpochMillis
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Article content source that fetches full article HTML from Habr.
 * Separated from FeedSource to clarify responsibilities:
 * - FeedSource: handles RSS feed listing
 * - This: handles full article content loading
 */
class HabrArticleContentSource(
    private val client: HttpClient,
    private val extractor: HabrArticleContentExtractor = HabrArticleContentExtractor(),
) : ArticleContentSource, ArticleCommentsSource {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getArticleByUrl(url: String): ArticleContent {
        val normalizedUrl = normalizeArticleUrl(url)
        val html = client.get(normalizedUrl).bodyAsText()
        return extractor.extract(
            articleId = extractArticleId(normalizedUrl) ?: "",
            articleUrl = normalizedUrl,
            html = html,
        )
    }

    override suspend fun getCommentsByUrl(url: String): List<CommentNode> {
        val articleId = extractArticleId(url) ?: return emptyList()
        val baseUrl = normalizeArticleUrl(url)
        val isThreadType = url.contains("/companies/") || url.contains("/posts/") || url.contains("/news/")

        val primaryUrl = "https://habr.com/kek/v2/${if (isThreadType) "threads" else "articles"}/$articleId/comments/"
        val fallbackUrl = "https://habr.com/kek/v2/${if (isThreadType) "articles" else "threads"}/$articleId/comments/"

        val primaryBody = client.get(primaryUrl).let { response ->
            if (response.status.isSuccess()) response.bodyAsText() else null
        } ?: return parseComments(client.get(fallbackUrl).bodyAsText(), baseUrl)

        return parseComments(primaryBody, baseUrl)
    }

    private fun parseComments(body: String, baseUrl: String): List<CommentNode> = runCatching {
        val response = json.decodeFromString<CommentsApiResponse>(body)
        buildCommentTree(response.comments, baseUrl)
    }.getOrDefault(emptyList())

    private fun buildCommentTree(
        rawComments: Map<String, CommentsApiComment>,
        baseUrl: String,
    ): List<CommentNode> {
        val nodes = mutableMapOf<String, MutableCommentNode>()
        rawComments.values.forEach { raw ->
            if (raw.isSuspended || raw.status == "deleted") return@forEach
            nodes[raw.id] = MutableCommentNode(
                id = raw.id,
                author = raw.author?.let {
                    Author(
                        id = it.id ?: "author-${(it.alias ?: it.fullname).hashCode()}",
                        displayName = it.fullname?.takeIf(String::isNotBlank) ?: it.alias ?: "Аноним",
                        profileUrl = null,
                    )
                },
                publishedAt = raw.timePublished,
                publishedAtEpoch = raw.timePublished?.toEpochMillis(),
                body = parseCommentMessage(raw.message, baseUrl),
                children = mutableListOf(),
            )
        }

        val attached = mutableSetOf<String>()
        rawComments.values.forEach { raw ->
            val node = nodes[raw.id] ?: return@forEach
            raw.children.forEach { childId ->
                nodes[childId]?.let { child ->
                    node.children.add(child)
                    attached.add(childId)
                }
            }
        }

        val roots = rawComments.values
            .asSequence()
            .filter { it.id !in attached }
            .mapNotNull { nodes[it.id] }
            .sortedBy { it.publishedAtEpoch ?: Long.MAX_VALUE }
            .map { it.toDomain() }
            .toList()

        return roots
    }

    private fun parseCommentMessage(message: String, baseUrl: String): List<ArticleBlock> {
        val blocks = HtmlArticleParser.parse(message, baseUrl)
        return blocks.ifEmpty { listOf(ArticleBlock.Paragraph(emptyList())) }
    }

    private class MutableCommentNode(
        val id: String,
        val author: Author?,
        val publishedAt: String?,
        val publishedAtEpoch: Long?,
        val body: List<ArticleBlock>,
        val children: MutableList<MutableCommentNode>,
    ) {
        fun toDomain(): CommentNode = CommentNode(
            id = id,
            author = author,
            publishedAt = publishedAt,
            body = body,
            children = children.map { it.toDomain() },
        )
    }

    private fun normalizeArticleUrl(url: String): String {
        val value = url.replace("&amp;", "&").trim()
        return when {
            value.startsWith("http://") || value.startsWith("https://") -> value
            value.all { it.isDigit() } -> "https://habr.com/ru/articles/$value/"
            else -> value
        }
    }

    private fun extractArticleId(url: String): String? {
        // Handles: /articles/123/, /posts/123/, /news/123/, /companies/x/posts/123/
        val regex = Regex("""/(?:articles|posts|news)/(\d+)/?""")
        return regex.find(url)?.groupValues?.getOrNull(1)
    }
}

@Serializable
internal data class CommentsApiResponse(
    @SerialName("comments") val comments: Map<String, CommentsApiComment> = emptyMap(),
    @SerialName("moderated") val moderated: Map<String, CommentsApiComment> = emptyMap(),
    @SerialName("threads") val threads: List<CommentsApiThread> = emptyList(),
    @SerialName("pinnedCommentIds") val pinnedCommentIds: List<String> = emptyList(),
    @SerialName("lastCommentTimestamp") val lastCommentTimestamp: String? = null,
    @SerialName("cacheKey") val cacheKey: String? = null,
)

@Serializable
internal data class CommentsApiThread(
    @SerialName("id") val id: String = "",
    @SerialName("comments") val comments: List<String> = emptyList(),
)

@Serializable
internal data class CommentsApiComment(
    @SerialName("id") val id: String,
    @SerialName("parentId") val parentId: String? = null,
    @SerialName("level") val level: Int = 0,
    @SerialName("timePublished") val timePublished: String? = null,
    @SerialName("timeChanged") val timeChanged: String? = null,
    @SerialName("isSuspended") val isSuspended: Boolean = false,
    @SerialName("status") val status: String? = null,
    @SerialName("score") val score: Int = 0,
    @SerialName("votesCount") val votesCount: Int = 0,
    @SerialName("message") val message: String = "",
    @SerialName("editorVersion") val editorVersion: Int = 1,
    @SerialName("author") val author: CommentsApiAuthor? = null,
    @SerialName("isPinned") val isPinned: Boolean = false,
    @SerialName("children") val children: List<String> = emptyList(),
)

@Serializable
internal data class CommentsApiAuthor(
    @SerialName("id") val id: String? = null,
    @SerialName("alias") val alias: String? = null,
    @SerialName("fullname") val fullname: String? = null,
    @SerialName("avatarUrl") val avatarUrl: String? = null,
    @SerialName("speciality") val speciality: String? = null,
)
