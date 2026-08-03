package com.arny.habrrss.data.sync

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SyncLocks {
    private val guard = Mutex()
    private val locks = mutableMapOf<String, Mutex>()

    suspend fun <T> withSourceLock(sourceKey: String, block: suspend () -> T): T {
        val lock = guard.withLock {
            locks.getOrPut(sourceKey) { Mutex() }
        }
        return lock.withLock { block() }
    }
}
