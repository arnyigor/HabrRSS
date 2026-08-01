package com.arny.habrrss.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arny.habrrss.data.repository.TechReaderRepository
import com.arny.habrrss.domain.models.ArticleContent
import com.arny.habrrss.domain.models.CommentNode
import com.arny.habrrss.domain.models.FeedItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ArticleUiState(
    val articleId: String? = null,
    val article: ArticleContent? = null,
    val isLoading: Boolean = false,
    val isBookmarked: Boolean = false,
    val comments: List<CommentNode> = emptyList(),
    val relatedArticles: List<FeedItem> = emptyList(),
    val isLoadingExtras: Boolean = false,
    val errorMessage: String? = null,
)

sealed interface ArticleIntent {
    data class Open(val articleId: String) : ArticleIntent
    data class OpenUrl(val url: String) : ArticleIntent
    data object Close : ArticleIntent
    data object ToggleBookmark : ArticleIntent
}

class ArticleViewModel(
    private val repository: TechReaderRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ArticleUiState())
    val state: StateFlow<ArticleUiState> = mutableState

    private var articleJob: Job? = null
    private var bookmarkJob: Job? = null

    fun dispatch(intent: ArticleIntent) {
        when (intent) {
            is ArticleIntent.Open -> openArticle(intent.articleId)
            is ArticleIntent.OpenUrl -> openArticleUrl(intent.url)
            ArticleIntent.Close -> close()
            ArticleIntent.ToggleBookmark -> toggleBookmark()
        }
    }

    fun openArticle(articleId: String) {
        val current = mutableState.value
        if (current.articleId == articleId && (current.article != null || current.isLoading)) return
        observeBookmark(articleId)
        articleJob?.cancel()
        articleJob = viewModelScope.launch {
            mutableState.update {
                it.copy(
                    articleId = articleId,
                    article = null,
                    isLoading = true,
                    comments = emptyList(),
                    relatedArticles = emptyList(),
                    isLoadingExtras = false,
                    errorMessage = null,
                )
            }
            try {
                val article = repository.getArticle(articleId)
                mutableState.update {
                    it.copy(
                        articleId = article.id,
                        article = article,
                        isLoading = false,
                        isBookmarked = repository.isBookmarked(article.id),
                        errorMessage = null,
                    )
                }
                observeBookmark(article.id)
                loadExtras(article.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableState.update {
                    it.copy(isLoading = false, errorMessage = e.message ?: "Ошибка загрузки статьи")
                }
            }
        }
    }

    fun openArticleUrl(url: String) {
        articleJob?.cancel()
        articleJob = viewModelScope.launch {
            mutableState.update {
                it.copy(
                    article = null,
                    isLoading = true,
                    comments = emptyList(),
                    relatedArticles = emptyList(),
                    isLoadingExtras = false,
                    errorMessage = null,
                )
            }
            try {
                val article = repository.getArticleByUrl(url)
                mutableState.update {
                    it.copy(
                        articleId = article.id,
                        article = article,
                        isLoading = false,
                        isBookmarked = repository.isBookmarked(article.id),
                        errorMessage = null,
                    )
                }
                observeBookmark(article.id)
                loadExtras(article.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableState.update {
                    it.copy(isLoading = false, errorMessage = e.message ?: "Ошибка загрузки статьи")
                }
            }
        }
    }

    fun toggleBookmark() {
        val articleId = mutableState.value.articleId ?: return
        viewModelScope.launch {
            repository.toggleBookmark(articleId)
            mutableState.update { it.copy(isBookmarked = repository.isBookmarked(articleId)) }
        }
    }

    fun close() {
        articleJob?.cancel()
        articleJob = null
        bookmarkJob?.cancel()
        bookmarkJob = null
        mutableState.value = ArticleUiState()
    }

    private fun loadExtras(articleId: String) {
        viewModelScope.launch {
            mutableState.update { it.copy(isLoadingExtras = true) }
            println("[ARTICLE-VM] loadExtras start for articleId=$articleId")
            try {
                val comments = repository.getArticleComments(articleId)
                println("[ARTICLE-VM] loadExtras comments loaded: ${comments.size}")
                val related = repository.getRelatedArticles(articleId)
                println("[ARTICLE-VM] loadExtras related loaded: ${related.size}")
                mutableState.update { state ->
                    if (state.articleId == articleId) {
                        state.copy(comments = comments, relatedArticles = related, isLoadingExtras = false)
                    } else {
                        state
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("[ARTICLE-VM] loadExtras error: ${e::class.simpleName}: ${e.message}")
                mutableState.update { it.copy(isLoadingExtras = false) }
            }
        }
    }

    private fun observeBookmark(articleId: String) {
        bookmarkJob?.cancel()
        bookmarkJob = viewModelScope.launch {
            repository.observeArticleItem(articleId).collect { item ->
                mutableState.update { it.copy(isBookmarked = item?.isBookmarked ?: it.isBookmarked) }
            }
        }
    }
}
