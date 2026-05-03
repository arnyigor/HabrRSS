package com.arny.habrrss.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.arny.habrrss.domain.models.ArticleContent
import com.arny.habrrss.domain.usecases.OpenArticleUseCase
import com.arny.habrrss.navigation.Screen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ArticleViewModel(
    savedStateHandle: SavedStateHandle,
    private val openArticle: OpenArticleUseCase,
) : ViewModel() {
    private val articleId: String = savedStateHandle.toRoute<Screen.Article>().articleId

    private val mutableState = MutableStateFlow<ArticleState>(ArticleState.Loading)
    val state: StateFlow<ArticleState> = mutableState

    init {
        loadArticle()
    }

    private fun loadArticle() {
        viewModelScope.launch {
            try {
                val article = openArticle(articleId)
                mutableState.update { ArticleState.Success(article) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableState.update {
                    ArticleState.Error(error.message ?: "Ошибка загрузки статьи")
                }
            }
        }
    }
}

sealed interface ArticleState {
    data object Loading : ArticleState
    data class Success(val article: ArticleContent) : ArticleState
    data class Error(val message: String) : ArticleState
}
