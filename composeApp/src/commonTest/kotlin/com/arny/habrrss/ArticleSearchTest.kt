package com.arny.habrrss

import com.arny.habrrss.domain.models.ArticleBlock
import com.arny.habrrss.domain.models.InlineNode
import com.arny.habrrss.ui.article.findSearchMatches
import com.arny.habrrss.ui.article.highlightRanges
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArticleSearchTest {

    @Test
    fun findsCaseInsensitiveRanges() {
        val ranges = highlightRanges("Kotlin и kotlin multiplatform", "kotlin")
        assertEquals(listOf(0 until 6, 9 until 15), ranges)
    }

    @Test
    fun emptyQueryYieldsNoRanges() {
        assertTrue(highlightRanges("text", "").isEmpty())
        assertTrue(highlightRanges("text", "   ").isEmpty())
        assertTrue(highlightRanges("", "kotlin").isEmpty())
    }

    @Test
    fun findsMatchesAcrossBlockTypes() {
        val blocks = listOf(
            ArticleBlock.Paragraph(inline = listOf(InlineNode.Text("Введение в архитектуру"))),
            ArticleBlock.CodeBlock(language = "kotlin", code = "fun main() { }"),
            ArticleBlock.ListBlock(
                ordered = false,
                items = listOf(
                    listOf(ArticleBlock.Paragraph(inline = listOf(InlineNode.Text("Архитектура микросервисов")))),
                ),
            ),
            ArticleBlock.TableBlock(
                rows = listOf(
                    listOf(listOf(ArticleBlock.Paragraph(inline = listOf(InlineNode.Text("Столбец архитектуры"))))),
                ),
            ),
            ArticleBlock.Paragraph(inline = listOf(InlineNode.Text("Заключение"))),
        )

        val matches = findSearchMatches(blocks, "архитектур")

        assertEquals(listOf(0, 2, 3), matches.map { it.blockIndex })
        assertEquals(1, matches[0].ranges.size)
    }

    @Test
    fun queryInCodeBlockIsFound() {
        val blocks = listOf(
            ArticleBlock.CodeBlock(language = "kotlin", code = "val repository = Repository()"),
            ArticleBlock.Paragraph(inline = listOf(InlineNode.Text("Просто текст"))),
        )
        val matches = findSearchMatches(blocks, "repository")
        assertEquals(listOf(0), matches.map { it.blockIndex })
    }

    @Test
    fun noMatchReturnsEmpty() {
        val blocks = listOf(ArticleBlock.Paragraph(inline = listOf(InlineNode.Text("Привет мир"))))
        assertTrue(findSearchMatches(blocks, "zzz").isEmpty())
        assertTrue(findSearchMatches(blocks, "").isEmpty())
    }

    @Test
    fun quoteAndSpoilerAreSearched() {
        val blocks = listOf(
            ArticleBlock.Quote(blocks = listOf(ArticleBlock.Paragraph(inline = listOf(InlineNode.Text("Цитата про поиск"))))),
            ArticleBlock.Spoiler(
                title = "Спойлер",
                blocks = listOf(ArticleBlock.Paragraph(inline = listOf(InlineNode.Text("Скрытый поиск")))),
            ),
        )
        val matches = findSearchMatches(blocks, "поиск")
        assertEquals(listOf(0, 1), matches.map { it.blockIndex })
    }
}
