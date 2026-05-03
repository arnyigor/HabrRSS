package com.arny.habrrss

import com.arny.habrrss.data.database.FileBackedFeedDao
import com.arny.habrrss.di.AndroidAppContainer
import com.arny.habrrss.di.AppContainer
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class AppContainerTest {
    @Test
    fun createsPresenterAndPlatformDaoOnJvm() {
        val container = AppContainer(enableLogging = false)
        try {
            assertIs<FileBackedFeedDao>(container.feedDao)
            assertNotNull(container.repository)
            assertNotNull(container.createReaderPresenter())
        } finally {
            container.close()
        }
    }

    @Test
    fun androidContainerCloseIsIdempotent() {
        val container = AndroidAppContainer(enableLogging = false)
        container.close()
        container.close()
    }
}
