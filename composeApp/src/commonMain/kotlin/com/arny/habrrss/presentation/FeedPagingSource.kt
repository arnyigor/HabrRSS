package com.arny.habrrss.presentation

import app.cash.paging.PagingSource
import app.cash.paging.PagingSourceLoadParams
import app.cash.paging.PagingSourceLoadResult
import app.cash.paging.PagingSourceLoadResultError
import app.cash.paging.PagingSourceLoadResultPage
import app.cash.paging.PagingState
import com.arny.habrrss.data.repository.TechReaderRepository
import com.arny.habrrss.domain.models.FeedItem

/**
 * Paging 3 source used as the pagination engine. The UI still renders a DB-backed Flow snapshot:
 * this source only decides when to request network pages and writes them into the database via the
 * repository. The database remains the single source of truth for screen state.
 */
class FeedPagingSource(
    private val repository: TechReaderRepository,
    private val feedId: String,
) : PagingSource<Int, FeedItem>() {
    override suspend fun load(params: PagingSourceLoadParams<Int>): PagingSourceLoadResult<Int, FeedItem> {
        return runCatching {
            val pageIndex = params.key ?: FIRST_PAGE
            val page = if (pageIndex == FIRST_PAGE) {
                repository.refreshFeed(feedId)
            } else {
                repository.loadNextPage(feedId)
            }
            PagingSourceLoadResultPage(
                data = page?.items.orEmpty(),
                prevKey = if (pageIndex == FIRST_PAGE) null else pageIndex - 1,
                nextKey = if (page?.nextCursor != null) pageIndex + 1 else null,
            )
        }.getOrElse { error ->
            PagingSourceLoadResultError(error)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, FeedItem>): Int? =
        state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }

    private companion object {
        private const val FIRST_PAGE = 0
    }
}
