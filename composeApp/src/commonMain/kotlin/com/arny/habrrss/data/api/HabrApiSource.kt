package com.arny.habrrss.data.api

import com.arny.habrrss.data.remote.habr.HabrApiClient
import com.arny.habrrss.data.remote.habr.HabrArticlesRequest
import com.arny.habrrss.data.remote.habr.HabrPeriod
import com.arny.habrrss.data.remote.habr.HabrSearchOrder
import com.arny.habrrss.data.remote.habr.error.HabrRemoteException
import com.arny.habrrss.data.remote.habr.mapper.HabrArticleMapper
import com.arny.habrrss.domain.models.ArticleContent
import com.arny.habrrss.domain.models.CommentNode
import com.arny.habrrss.domain.models.FeedDescriptor
import com.arny.habrrss.domain.models.FeedKind
import com.arny.habrrss.domain.models.FeedPage
import com.arny.habrrss.domain.models.PageCursor
import com.arny.habrrss.domain.source.FeedSource
import com.arny.habrrss.domain.source.SourceUnavailableException
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlin.time.Clock

class HabrApiSource(
    client: HttpClient,
    private val api: HabrApiClient = HabrApiClient(client),
    private val mapper: HabrArticleMapper = HabrArticleMapper(),
) : FeedSource {
    private val feeds = listOf(
        FeedDescriptor(
            id = FeedIds.All,
            title = "Новые",
            sourceTitle = "Habr API",
            url = "https://habr.com/ru/articles/",
            description = "Свежие статьи Хабра через /kek/v2",
            kind = FeedKind.All,
        ),
    )

    override suspend fun getFeeds(): List<FeedDescriptor> = feeds

    override suspend fun getItems(feedId: String, page: PageCursor?): FeedPage {
        val request = requestFor(feedId, page)
            ?: return FeedPage(emptyList(), null, fromCache = false, updatedAt = null)

        return try {
            val response = api.getArticles(request)
            val items = mapper.orderedArticles(response).map { dto ->
                mapper.toFeedItem(dto, feedId)
            }
            FeedPage(
                items = items,
                nextCursor = request.nextCursor(response.pagesCount),
                fromCache = false,
                updatedAt = Clock.System.now().toEpochMilliseconds().toString(),
                loadedPage = request.page,
                pagesCount = response.pagesCount,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: HabrRemoteException) {
            throw error
        } catch (error: Exception) {
            throw SourceUnavailableException("Habr API source is unavailable: ${error.message}")
        }
    }

    @Deprecated("Use ArticleContentSource instead", ReplaceWith("ArticleContentSource"))
    override suspend fun getArticle(articleId: String): ArticleContent {
        throw SourceUnavailableException("Use HabrArticleContentSource for article details.")
    }

    override suspend fun getComments(articleId: String): List<CommentNode> = emptyList()

    private fun requestFor(feedId: String, page: PageCursor?): HabrArticlesRequest? {
        val pageNumber = page?.value?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        return when {
            feedId == FeedIds.All -> HabrArticlesRequest.Latest(page = pageNumber)
            feedId.startsWith(FeedIds.HubPrefix) -> {
                val parts = feedId.removePrefix(FeedIds.HubPrefix).split(':')
                val alias = parts.getOrNull(0)?.takeIf(String::isNotBlank) ?: return null
                val period = parts.getOrNull(1)?.toPeriodOrNull() ?: HabrPeriod.AllTime
                HabrArticlesRequest.Hub(alias = alias, period = period, page = pageNumber)
            }
            feedId.startsWith(FeedIds.CompanyPrefix) -> {
                val alias = feedId.removePrefix(FeedIds.CompanyPrefix).takeIf(String::isNotBlank) ?: return null
                HabrArticlesRequest.Company(alias = alias, page = pageNumber)
            }
            feedId.startsWith(FeedIds.AuthorPrefix) -> {
                val alias = feedId.removePrefix(FeedIds.AuthorPrefix).takeIf(String::isNotBlank) ?: return null
                HabrArticlesRequest.Author(alias = alias, page = pageNumber)
            }
            feedId.startsWith(FeedIds.SearchPrefix) -> {
                val parts = feedId.removePrefix(FeedIds.SearchPrefix).split(':')
                val query = parts.getOrNull(0)?.takeIf(String::isNotBlank) ?: return null
                val order = parts.getOrNull(1)?.toSearchOrderOrNull() ?: HabrSearchOrder.Date
                HabrArticlesRequest.Search(query = query, order = order, page = pageNumber)
            }
            else -> null
        }
    }

    private fun HabrArticlesRequest.nextCursor(pagesCount: Int): PageCursor? =
        if (page < pagesCount) PageCursor((page + 1).toString()) else null

    private fun String.toPeriodOrNull(): HabrPeriod? =
        HabrPeriod.entries.firstOrNull { it.wireValue == this }

    private fun String.toSearchOrderOrNull(): HabrSearchOrder? =
        HabrSearchOrder.entries.firstOrNull { it.wireValue == this }

    object FeedIds {
        const val All = "habr-all"
        const val AllCached = "local-all"
        const val HubPrefix = "habr-hub:"
        const val CompanyPrefix = "habr-company:"
        const val AuthorPrefix = "habr-author:"
        const val SearchPrefix = "habr-search:"

        fun hub(alias: String, period: HabrPeriod = HabrPeriod.AllTime): String =
            "$HubPrefix$alias:${period.wireValue}"

        fun company(alias: String): String = "$CompanyPrefix$alias"

        fun author(alias: String): String = "$AuthorPrefix$alias"

        fun search(query: String, order: HabrSearchOrder = HabrSearchOrder.Date): String =
            "$SearchPrefix$query:${order.wireValue}"
    }
}
