package com.arny.habrrss.core.logging

import android.util.Log

internal actual object PlatformLog {
    actual fun d(tag: String, message: String) {
        logOrPrint(tag, message, null) { Log.d(tag, message) }
    }

    actual fun i(tag: String, message: String) {
        logOrPrint(tag, message, null) { Log.i(tag, message) }
    }

    actual fun w(tag: String, message: String, throwable: Throwable?) {
        logOrPrint(tag, message, throwable) {
            if (throwable == null) {
                Log.w(tag, message)
            } else {
                Log.w(tag, message, throwable)
            }
        }
    }

    actual fun e(tag: String, message: String, throwable: Throwable?) {
        logOrPrint(tag, message, throwable) {
            if (throwable == null) {
                Log.e(tag, message)
            } else {
                Log.e(tag, message, throwable)
            }
        }
    }

    actual fun currentThreadName(): String = Thread.currentThread().name

    private inline fun logOrPrint(tag: String, message: String, throwable: Throwable?, log: () -> Int) {
        try {
            log()
        } catch (_: RuntimeException) {
            println("$tag: $message")
            if (throwable != null) {
                println("$tag: ${throwable::class.simpleName}: ${throwable.message}")
            }
        }
    }
}
