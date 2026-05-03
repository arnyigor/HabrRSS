package com.arny.habrrss

import com.arny.habrrss.data.article.HabrArticleContentExtractor
import com.arny.habrrss.domain.models.ArticleBlock
import com.arny.habrrss.ui.article.plainText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HabrArticleContentExtractorTest {
    @Test
    fun extractsFullArticleBodyFromHabrPage() {
        val html = """
            <html>
              <head>
                <meta property="og:image" content="https://habr.com/share.png">
              </head>
              <body>
                <article class="tm-article-presenter__content">
                  <h1>RSS summary is not enough</h1>
                  <div id="post-content-body">
                    <div class="article-formatted-body article-formatted-body_version-2">
                      <div>
                        <h2>Native reader mode</h2>
                        <p>Первый большой абзац полной статьи, который точно длиннее краткого RSS-анонса и должен попасть в Compose reader. Здесь описывается основная идея материала, контекст, проблема и несколько важных деталей, которые нельзя заменять короткой ссылкой.</p>
                        <p>Второй большой абзац полной статьи с <strong>жирным текстом</strong>, ссылкой <a href="/ru/articles/42/">внутри</a> и полезным содержанием. Этот текст нужен для проверки, что extractor действительно достал полный body статьи, а не короткое описание из RSS.</p>
                        <pre><code class="language-kotlin">fun fullArticle() = "loaded"</code></pre>
                        <a href="https://habr.com/ru/articles/42/#habracut">Читать далее</a>
                      </div>
                    </div>
                  </div>
                </article>
              </body>
            </html>
        """.trimIndent()

        val article = HabrArticleContentExtractor().extract(
            articleId = "article-42",
            articleUrl = "https://habr.com/ru/articles/42/?utm_source=habrahabr&amp;utm_medium=rss",
            html = html,
        )

        assertEquals("https://habr.com/ru/articles/42/", article.url)
        assertEquals("RSS summary is not enough", article.title)
        assertEquals("Полная статья загружена с Habr.", article.sourceNotice)
        assertTrue(article.blocks.size >= 4)
        assertTrue(article.blocks.any { it is ArticleBlock.CodeBlock })
        assertFalse(article.blocks.joinToString(" ") { it.textForTest() }.contains("Читать далее"))
    }

    @Test
    fun rejectsPagesWithoutFullArticleBody() {
        val error = assertFailsWith<RuntimeException> {
            HabrArticleContentExtractor().extract(
                articleId = "missing",
                articleUrl = "https://habr.com/ru/articles/missing/",
                html = "<html><body><main>No article here</main></body></html>",
            )
        }

        assertTrue(error.message.orEmpty().contains("Article body not found"))
    }

    @Test
    fun rejectsTooShortArticleBodies() {
        val error = assertFailsWith<RuntimeException> {
            HabrArticleContentExtractor().extract(
                articleId = "short",
                articleUrl = "https://habr.com/ru/articles/short/",
                html = """
                    <html>
                      <body>
                        <div id="post-content-body">
                          <div class="article-formatted-body">
                            <p>Слишком коротко.</p>
                          </div>
                        </div>
                      </body>
                    </html>
                """.trimIndent(),
            )
        }

        assertTrue(error.message.orEmpty().contains("too short"))
    }
}

private fun ArticleBlock.textForTest(): String = when (this) {
    is ArticleBlock.CodeBlock -> code
    is ArticleBlock.Heading -> inline.plainText()
    is ArticleBlock.Image -> alt.orEmpty()
    is ArticleBlock.ListBlock -> items.flatten().joinToString(" ") { it.textForTest() }
    is ArticleBlock.Paragraph -> inline.plainText()
    is ArticleBlock.Quote -> blocks.joinToString(" ") { it.textForTest() }
    is ArticleBlock.Spoiler -> blocks.joinToString(" ") { it.textForTest() }
    is ArticleBlock.TableBlock -> rows.flatten().flatten().joinToString(" ") { it.textForTest() }
    is ArticleBlock.UnknownHtml -> html
}
