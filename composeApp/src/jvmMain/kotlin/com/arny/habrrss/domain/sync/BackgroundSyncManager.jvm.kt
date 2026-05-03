package com.arny.habrrss.domain.sync

import com.arny.habrrss.data.repository.TechReaderRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.hours

actual fun createBackgroundSyncManager(
    repository: TechReaderRepository,
): BackgroundSyncManager = DesktopBackgroundSyncManager(repository)

private class DesktopBackgroundSyncManager(
    private val repository: TechReaderRepository,
) : BackgroundSyncManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var syncJob: Job? = null

    override fun startPeriodicSync() {
        if (syncJob?.isActive == true) return

        syncJob = scope.launch {
            while (isActive) {
                try {
                    repository.getFeeds().firstOrNull()?.id?.let { feedId ->
                        repository.refreshFeed(feedId)
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // Keep periodic sync alive; foreground refresh reports user-visible errors.
                }
                delay(1.hours)
            }
        }
    }

    override fun stopSync() {
        syncJob?.cancel()
        syncJob = null
    }

    fun close() {
        scope.cancel()
    }
}
