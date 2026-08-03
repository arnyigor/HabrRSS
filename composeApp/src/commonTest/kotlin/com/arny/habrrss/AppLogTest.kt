package com.arny.habrrss

import com.arny.habrrss.core.logging.AppLog
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AppLogTest {
    @Test
    fun measureReturnsBlockResult() {
        val result = AppLog.measure("Test", "sync operation") {
            "done"
        }

        assertEquals("done", result)
    }

    @Test
    fun measureSuspendReturnsBlockResult() = runTest {
        val result = AppLog.measureSuspend("Test", "suspend operation") {
            "done"
        }

        assertEquals("done", result)
    }

    @Test
    fun measureRethrowsBlockFailure() {
        val error = assertFailsWith<IllegalStateException> {
            AppLog.measure("Test", "failing operation") {
                error("boom")
            }
        }

        assertEquals("boom", error.message)
    }
}
