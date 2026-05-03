package com.arny.habrrss.domain.sync

import com.arny.habrrss.data.repository.TechReaderRepository

actual fun createBackgroundSyncManager(
    repository: TechReaderRepository,
): BackgroundSyncManager = AndroidBackgroundSyncManager()

private class AndroidBackgroundSyncManager : BackgroundSyncManager {
    override fun startPeriodicSync() = Unit

    override fun stopSync() = Unit
}
