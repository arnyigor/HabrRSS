package com.arny.habrrss

import com.arny.habrrss.ui.feed.withoutHabrMetadata
import kotlin.test.Test
import kotlin.test.assertEquals

class FeedCardMetadataTest {
    @Test
    fun leavesSummaryWithoutHabrMetadataUntouched() {
        val summary = "Plain summary without embedded metadata."

        assertEquals(summary, summary.withoutHabrMetadata())
    }

    @Test
    fun stripsEmbeddedHabrMetadataFromSummary() {
        val summary = "Article preview.\n\nХабы: Kotlin, Android\nМетки: compose, rss"

        assertEquals("Article preview.", summary.withoutHabrMetadata())
    }
}
