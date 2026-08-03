package com.arny.habrrss.core.logging

import kotlin.time.Clock

internal actual object PlatformLog {
    actual fun d(tag: String, message: String) {
        printLog("D", tag, message, null)
    }

    actual fun i(tag: String, message: String) {
        printLog("I", tag, message, null)
    }

    actual fun w(tag: String, message: String, throwable: Throwable?) {
        printLog("W", tag, message, throwable)
    }

    actual fun e(tag: String, message: String, throwable: Throwable?) {
        printLog("E", tag, message, throwable)
    }

    actual fun currentThreadName(): String = Thread.currentThread().name

    private fun printLog(level: String, tag: String, message: String, throwable: Throwable?) {
        println("${Clock.System.now()} $level/$tag: $message")
        if (throwable != null) {
            println(throwable.stackTraceToString())
        }
    }
}
