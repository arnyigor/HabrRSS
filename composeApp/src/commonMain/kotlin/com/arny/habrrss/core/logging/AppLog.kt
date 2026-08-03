package com.arny.habrrss.core.logging

import kotlin.time.Clock

object AppLog {
    fun d(tag: String, message: String) {
        PlatformLog.d(fullTag(tag), withThread(message))
    }

    fun i(tag: String, message: String) {
        PlatformLog.i(fullTag(tag), withThread(message))
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        PlatformLog.w(fullTag(tag), withThread(message), throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        PlatformLog.e(fullTag(tag), withThread(message), throwable)
    }

    @Suppress("TooGenericExceptionCaught")
    inline fun <T> measure(tag: String, operation: String, block: () -> T): T {
        val startedAt = Clock.System.now().toEpochMilliseconds()
        return try {
            block().also {
                i(tag, "$operation completed in ${Clock.System.now().toEpochMilliseconds() - startedAt}ms")
            }
        } catch (error: Throwable) {
            e(tag, "$operation failed after ${Clock.System.now().toEpochMilliseconds() - startedAt}ms", error)
            throw error
        }
    }

    @Suppress("TooGenericExceptionCaught")
    suspend inline fun <T> measureSuspend(tag: String, operation: String, crossinline block: suspend () -> T): T {
        val startedAt = Clock.System.now().toEpochMilliseconds()
        return try {
            block().also {
                i(tag, "$operation completed in ${Clock.System.now().toEpochMilliseconds() - startedAt}ms")
            }
        } catch (error: Throwable) {
            e(tag, "$operation failed after ${Clock.System.now().toEpochMilliseconds() - startedAt}ms", error)
            throw error
        }
    }

    private fun fullTag(tag: String): String = "HabrRSS.$tag"

    private fun withThread(message: String): String = "[thread=${PlatformLog.currentThreadName()}] $message"
}

internal expect object PlatformLog {
    fun d(tag: String, message: String)
    fun i(tag: String, message: String)
    fun w(tag: String, message: String, throwable: Throwable?)
    fun e(tag: String, message: String, throwable: Throwable?)
    fun currentThreadName(): String
}
