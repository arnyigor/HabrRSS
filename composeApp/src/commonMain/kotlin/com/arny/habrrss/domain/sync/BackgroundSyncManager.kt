package com.arny.habrrss.domain.sync

import com.arny.habrrss.data.repository.TechReaderRepository

interface BackgroundSyncManager {
    fun startPeriodicSync()
    fun stopSync()
}

expect fun createBackgroundSyncManager(
    repository: TechReaderRepository,
): BackgroundSyncManager
