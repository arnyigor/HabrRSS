package com.arny.habrrss.domain.models

import kotlinx.serialization.Serializable

data class FeedDescriptor(
    val id: String,
    val title: String,
    val sourceTitle: String,
    val url: String,
    val description: String,
    val kind: FeedKind,
)

enum class FeedKind {
    All,
    Best,
    Posts,
    News,
    Hub,
    Tag,
    Search,
    Custom,
}

data class FeedItem(
    val id: String,
    val feedId: String,
    val title: String,
    val summary: String,
    val descriptionHtml: String? = null,
    val url: String,
    val imageUrl: String?,
    val author: Author?,
    val publishedAt: String?, // RFC 822/1123 string for display
    val publishedAtEpoch: Long?, // epoch milliseconds for sorting/comparison
    val tags: List<Tag>,
    val hubs: List<Hub>,
    val rating: String?,
    val commentsCount: Int?,
    val isRead: Boolean,
    val isBookmarked: Boolean,
)

data class FeedPage(
    val items: List<FeedItem>,
    val nextCursor: PageCursor?,
    val fromCache: Boolean,
    val updatedAt: String?,
    /** Total number of pages available for this feed, when the source reports it (API paging). */
    val totalPages: Int? = null,
)

data class PageCursor(
    val value: String,
    val direction: CursorDirection = CursorDirection.Next,
)

/**
 * Persisted progress of a multi-page archive import: how many pages are already in the local
 * database and how many the source reported in total (when known). Derived from the paging
 * cursor, so a cancelled or failed import resumes from the right page instead of restarting.
 */
data class FeedLoadProgress(
    val pagesProcessed: Int,
    val totalPages: Int?,
)

enum class CursorDirection {
    Next,
    Previous,
}

@Serializable
data class ArticleContent(
    val id: String,
    val title: String,
    val url: String,
    val imageUrl: String?,
    val author: Author?,
    val publishedAt: String?,
    val tags: List<Tag>,
    val hubs: List<Hub>,
    val blocks: List<ArticleBlock>,
    val sourceNotice: String,
)

@Serializable
sealed interface ArticleBlock {
    @Serializable
    data class Paragraph(val inline: List<InlineNode>) : ArticleBlock
    @Serializable
    data class Heading(val level: Int, val inline: List<InlineNode>) : ArticleBlock
    @Serializable
    data class Image(val url: String, val alt: String?) : ArticleBlock
    @Serializable
    data class CodeBlock(val language: String?, val code: String) : ArticleBlock
    @Serializable
    data class Quote(val blocks: List<ArticleBlock>) : ArticleBlock
    @Serializable
    data class ListBlock(val ordered: Boolean, val items: List<List<ArticleBlock>>) : ArticleBlock
    @Serializable
    data class TableBlock(val rows: List<List<List<ArticleBlock>>>) : ArticleBlock
    @Serializable
    data class Spoiler(val title: String, val blocks: List<ArticleBlock>) : ArticleBlock
    @Serializable
    data class UnknownHtml(val html: String) : ArticleBlock
}

@Serializable
sealed interface InlineNode {
    @Serializable
    data class Text(val value: String) : InlineNode
    @Serializable
    data class Link(val text: String, val url: String) : InlineNode
    @Serializable
    data class Code(val value: String) : InlineNode
    @Serializable
    data class Bold(val children: List<InlineNode>) : InlineNode
    @Serializable
    data class Italic(val children: List<InlineNode>) : InlineNode
}

data class CommentNode(
    val id: String,
    val author: Author?,
    val publishedAt: String?,
    val body: List<ArticleBlock>,
    val children: List<CommentNode>,
)

@Serializable
data class Tag(
    val id: String,
    val title: String,
)

@Serializable
data class Hub(
    val id: String,
    val title: String,
    val slug: String? = null,
)

@Serializable
data class Author(
    val id: String,
    val displayName: String,
    val profileUrl: String?,
)

data class ReadingState(
    val articleId: String,
    val isRead: Boolean,
    val lastOpenedAt: String?,
    val scrollPosition: Int,
)

data class Bookmark(
    val articleId: String,
    val createdAt: String,
    val note: String?,
)

data class FeedSettings(
    val themeMode: ThemeMode,
    val fontScale: Float,
    val lineHeightScale: Float,
    val compactCards: Boolean,
    val offlinePolicy: CachePolicy,
    val cacheSizeMb: Int,
    val autoRefreshMinutes: Int,
    val openLinksInsideApp: Boolean,
    val feedCardMode: String = "Comfortable",
) {
    companion object {
        fun defaults(): FeedSettings = FeedSettings(
            themeMode = ThemeMode.System,
            fontScale = 1f,
            lineHeightScale = 1.25f,
            compactCards = false,
            offlinePolicy = CachePolicy.OnlineFirst,
            cacheSizeMb = 256,
            autoRefreshMinutes = 30,
            openLinksInsideApp = false,
            feedCardMode = "Comfortable",
        )
    }
}

enum class ThemeMode {
    System,
    Light,
    Dark,
}

data class UserFilter(
    val hiddenTags: Set<String>,
    val hiddenAuthors: Set<String>,
    val hiddenKeywords: Set<String>,
    val showUnreadOnly: Boolean,
)

enum class CachePolicy {
    OnlineFirst,
    CacheFirst,
    OfflineOnly,
    RefreshInBackground,
}

data class ExportRequest(
    val articleId: String,
    val format: ExportFormat,
    val includeImages: Boolean,
    val targetPath: String?,
)

enum class ExportFormat {
    Markdown,
    Pdf,
}
