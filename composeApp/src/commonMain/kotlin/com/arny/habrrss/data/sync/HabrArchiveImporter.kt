package com.arny.habrrss.data.sync

import com.arny.habrrss.data.api.HabrApiSource
import com.arny.habrrss.data.database.FeedDao
import com.arny.habrrss.data.database.FeedItemEntity
import com.arny.habrrss.data.database.SyncStateEntity
import com.arny.habrrss.data.remote.habr.HabrApiClient
import com.arny.habrrss.data.remote.habr.HabrArticlesRequest
import com.arny.habrrss.data.remote.habr.HabrPeriod
import com.arny.habrrss.data.remote.habr.mapper.HabrArticleMapper
import com.arny.habrrss.domain.models.FeedItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Clock

class HabrArchiveImporter(
    private val api: HabrApiClient,
    private val feedDao: FeedDao,
    private val locks: SyncLocks = SyncLocks(),
    private val mapper: HabrArticleMapper = HabrArticleMapper(),
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val pageDelayMillis: Long = DEFAULT_PAGE_DELAY_MILLIS,
) {
    suspend fun importHub(alias: String) {
        val sourceKey = HabrApiSource.FeedIds.hub(alias, HabrPeriod.AllTime)
        locks.withSourceLock(sourceKey) {
            var state = feedDao.getSyncState(sourceKey) ?: newState(sourceKey, mode = MODE_HUB_ALLTIME)
            if (state.status == STATUS_COMPLETED) return@withSourceLock
            state = state.copy(status = STATUS_RUNNING, updatedAtEpochMillis = nowMillis())
            feedDao.upsertSyncState(state)

            try {
                while (currentCoroutineContext().isActive) {
                    val latestState = feedDao.getSyncState(sourceKey) ?: state
                    if (latestState.status == STATUS_PAUSED || latestState.status == STATUS_CANCELLED) break

                    val page = latestState.nextPage.coerceAtLeast(1)
                    val response = api.getArticles(
                        HabrArticlesRequest.Hub(
                            alias = alias,
                            period = HabrPeriod.AllTime,
                            page = page,
                        ),
                    )
                    val orderedArticles = mapper.orderedArticles(response)
                    val entities = orderedArticles.map { dto ->
                        mapper.toFeedItem(dto, sourceKey).toEntity(fetchedAt = nowMillis())
                    }
                    feedDao.insertAll(entities)

                    val processedState = latestState.copy(
                        status = STATUS_RUNNING,
                        nextPage = page + 1,
                        pagesCountSnapshot = response.pagesCount,
                        pagesProcessed = latestState.pagesProcessed + 1,
                        receivedCount = latestState.receivedCount + response.publicationIds.size,
                        uniqueCount = feedDao.getByFeedOnce(sourceKey).size.toLong(),
                        failedPage = null,
                        errorCode = null,
                        updatedAtEpochMillis = nowMillis(),
                    )
                    feedDao.upsertSyncState(processedState)
                    state = processedState

                    if (page >= response.pagesCount) {
                        reconcileWeekly(alias, sourceKey)
                        feedDao.upsertSyncState(
                            state.copy(
                                status = STATUS_COMPLETED,
                                completedAtEpochMillis = nowMillis(),
                                updatedAtEpochMillis = nowMillis(),
                            ),
                        )
                        break
                    }

                    if (pageDelayMillis > 0) delay(pageDelayMillis)
                }
            } catch (error: CancellationException) {
                feedDao.upsertSyncState(
                    state.copy(status = STATUS_PAUSED, updatedAtEpochMillis = nowMillis()),
                )
                throw error
            } catch (error: Exception) {
                feedDao.upsertSyncState(
                    state.copy(
                        status = STATUS_FAILED,
                        failedPage = state.nextPage,
                        errorCode = error::class.simpleName ?: "ImportError",
                        updatedAtEpochMillis = nowMillis(),
                    ),
                )
                throw error
            }
        }
    }

    suspend fun pauseImport(sourceKey: String) {
        updateStatus(sourceKey, STATUS_PAUSED)
    }

    suspend fun cancelImport(sourceKey: String) {
        updateStatus(sourceKey, STATUS_CANCELLED)
    }

    private suspend fun reconcileWeekly(alias: String, sourceKey: String) {
        val response = api.getArticles(
            HabrArticlesRequest.Hub(
                alias = alias,
                period = HabrPeriod.Weekly,
                page = 1,
            ),
        )
        val fetchedAt = nowMillis()
        feedDao.insertAll(
            mapper.orderedArticles(response).map { dto ->
                mapper.toFeedItem(dto, sourceKey).toEntity(fetchedAt = fetchedAt)
            },
        )
    }

    private suspend fun updateStatus(sourceKey: String, status: String) {
        val state = feedDao.getSyncState(sourceKey) ?: return
        feedDao.upsertSyncState(
            state.copy(
                status = status,
                updatedAtEpochMillis = nowMillis(),
                completedAtEpochMillis = if (status == STATUS_CANCELLED) nowMillis() else state.completedAtEpochMillis,
            ),
        )
    }

    private fun newState(sourceKey: String, mode: String): SyncStateEntity {
        val now = nowMillis()
        return SyncStateEntity(
            sourceKey = sourceKey,
            mode = mode,
            status = STATUS_RUNNING,
            nextPage = 1,
            pagesCountSnapshot = null,
            pagesProcessed = 0,
            receivedCount = 0,
            uniqueCount = 0,
            failedPage = null,
            errorCode = null,
            startedAtEpochMillis = now,
            updatedAtEpochMillis = now,
            completedAtEpochMillis = null,
        )
    }

    private fun FeedItem.toEntity(fetchedAt: Long): FeedItemEntity = FeedItemEntity(
        id = id,
        feedId = feedId,
        title = title,
        summary = summary,
        descriptionHtml = descriptionHtml,
        url = url,
        imageUrl = imageUrl,
        authorName = author?.displayName,
        authorProfileUrl = author?.profileUrl,
        publishedAt = publishedAt,
        publishedAtEpoch = publishedAtEpoch,
        tagsJson = json.encodeToString(tags),
        hubsJson = json.encodeToString(hubs),
        rating = rating,
        commentsCount = commentsCount,
        cachedArticleJson = null,
        fetchedAt = fetchedAt,
    )

    private fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

    companion object {
        const val MODE_HUB_ALLTIME = "hub_alltime"
        const val STATUS_RUNNING = "running"
        const val STATUS_PAUSED = "paused"
        const val STATUS_CANCELLED = "cancelled"
        const val STATUS_COMPLETED = "completed"
        const val STATUS_FAILED = "failed"
        private const val DEFAULT_PAGE_DELAY_MILLIS = 500L
    }
}
