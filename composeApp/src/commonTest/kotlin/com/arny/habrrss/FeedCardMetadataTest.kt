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

    @Test
    fun stripsLongFooterWithProseLikeTokensAndPunctuation() {
        // Real Habr footer: hubs and tags followed by a prose-looking fragment with punctuation.
        val summary = "О праве на следующий бюджет\n\n" +
            "Хабы: Управление продуктом, Управление разработкой, Исследования и прогнозы в IT " +
            "Метки: футурама Продолжение «Манифеста создателя», «Ли...»"

        assertEquals("О праве на следующий бюджет", summary.withoutHabrMetadata())
    }

    @Test
    fun keepsProseThatMentionsHubsWithoutFooter() {
        val summary = "Хабы: так в Habr называют тематические разделы. Далее идёт обычный текст статьи."

        assertEquals(summary, summary.withoutHabrMetadata())
    }
}
