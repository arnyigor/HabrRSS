package com.arny.habrrss.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.AndroidUiModes.UI_MODE_NIGHT_YES
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation3.runtime.NavKey
import com.arny.habrrss.domain.models.FeedSettings
import com.arny.habrrss.domain.models.ThemeMode
import com.arny.habrrss.navigation.Screen
import com.arny.habrrss.presentation.ArticleIntent
import com.arny.habrrss.presentation.ArticleUiState
import com.arny.habrrss.presentation.ArticleViewModel
import com.arny.habrrss.presentation.FeedIntent
import com.arny.habrrss.presentation.FeedViewModel
import com.arny.habrrss.presentation.ReaderDestination
import com.arny.habrrss.presentation.ReaderUiState
import com.arny.habrrss.ui.ReaderTheme
import com.arny.habrrss.ui.article.ArticleScreen
import com.arny.habrrss.ui.components.ErrorBanner
import com.arny.habrrss.ui.components.PlatformBackHandler
import com.arny.habrrss.ui.components.WideLayoutMinWidth
import com.arny.habrrss.ui.feed.FeedScreen
import com.arny.habrrss.ui.search.SearchScreen
import com.arny.habrrss.ui.settings.SettingsScreen
import com.arny.habrrss.ui.sources.SourceScreen

/** Stateful entry: owns Navigation 3 back stacks and connects ViewModels to stateless UI. */
@Composable
internal fun ReaderApp(
    state: ReaderUiState,
    viewModel: FeedViewModel,
    articleViewModel: ArticleViewModel,
) {
    val backStackManager = rememberReaderBackStackManager()
    val currentRoute = backStackManager.currentRoute
    val articleState by articleViewModel.state.collectAsState()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeContentPadding(),
    ) {
        val isWide = maxWidth >= WideLayoutMinWidth
        val displayRoute =
            if (isWide && currentRoute is Screen.Article) backStackManager.currentTab else currentRoute
        val isArticleRoute = !isWide && currentRoute is Screen.Article
        val closeArticle = {
            viewModel.dispatch(FeedIntent.CloseArticle)
            articleViewModel.close()
        }
        val navigateToTopLevel: (Screen) -> Unit = { screen ->
            backStackManager.selectTopLevel(screen)
            viewModel.dispatch(FeedIntent.SelectDestination(screen.toDestination()))
            closeArticle()
        }
        val popBackStack: () -> Unit = {
            val removed = backStackManager.popBackStack()
            if (removed is Screen.Article) {
                closeArticle()
            }
        }
        val openArticle: (String) -> Unit = { articleId ->
            viewModel.dispatch(FeedIntent.SelectArticle(articleId))
            articleViewModel.openArticle(articleId)
            if (!isWide) {
                backStackManager.navigate(Screen.Article(articleId))
            }
        }
        val navigateToFeed: () -> Unit = {
            backStackManager.selectTopLevel(Screen.Feed)
            viewModel.dispatch(FeedIntent.SelectDestination(Screen.Feed.toDestination()))
        }

        LaunchedEffect(isWide, state.isArticleOpen, state.selectedArticleId) {
            val articleId = state.selectedArticleId
            if (!isWide && state.isArticleOpen && articleId != null) {
                backStackManager.navigate(Screen.Article(articleId))
            }
        }

        ReaderAppContent(
            state = state,
            articleState = articleState,
            selectedTopLevel = backStackManager.currentTab,
            isArticleRoute = isArticleRoute,
            onRefresh = { viewModel.dispatch(FeedIntent.Refresh) },
            onSearchChanged = { viewModel.dispatch(FeedIntent.UpdateSearchQuery(it)) },
            onDestinationSelected = { viewModel.dispatch(FeedIntent.SelectDestination(it)) },
            onTopLevelSelected = navigateToTopLevel,
            onCloseArticle = closeArticle,
            onHubSelected = { hubId ->
                viewModel.dispatch(FeedIntent.SelectHub(hubId))
                navigateToFeed()
            },
            onHubFeedRequested = { slug, title ->
                viewModel.dispatch(FeedIntent.OpenHubFeed(slug, title))
                navigateToFeed()
            },
            onTagSelected = { tagId ->
                viewModel.dispatch(FeedIntent.SelectTag(tagId))
                navigateToFeed()
            },
            onFavoriteHubToggled = { viewModel.dispatch(FeedIntent.ToggleFavoriteHub(it)) },
            onFavoriteTagToggled = { viewModel.dispatch(FeedIntent.ToggleFavoriteTag(it)) },
            onArticleBookmarkToggled = { articleViewModel.dispatch(ArticleIntent.ToggleBookmark) },
            onRelatedArticleSelected = openArticle,
            onBack = popBackStack,
            onErrorDismiss = { viewModel.dispatch(FeedIntent.DismissError) },
            navHost = { modifier, wide ->
                AppNavHost(
                    backStack = if (wide) listOf(displayRoute) else backStackManager.currentBackStack,
                    state = state,
                    viewModel = viewModel,
                    articleViewModel = articleViewModel,
                    openArticle = openArticle,
                    isWide = wide,
                    onBack = popBackStack,
                    navigateToFeed = navigateToFeed,
                    modifier = modifier,
                )
            },
        )
    }
}

/** Stateless UI shell: all state and actions are hoisted for previews and tests. */
@Composable
internal fun ReaderAppContent(
    state: ReaderUiState,
    articleState: ArticleUiState,
    selectedTopLevel: Screen,
    isArticleRoute: Boolean,
    onRefresh: () -> Unit,
    onSearchChanged: (String) -> Unit,
    onDestinationSelected: (ReaderDestination) -> Unit,
    onTopLevelSelected: (Screen) -> Unit,
    onCloseArticle: () -> Unit,
    onHubSelected: (String?) -> Unit,
    onHubFeedRequested: (String, String) -> Unit,
    onTagSelected: (String?) -> Unit,
    onFavoriteHubToggled: (String) -> Unit,
    onFavoriteTagToggled: (String) -> Unit,
    onArticleBookmarkToggled: () -> Unit,
    onRelatedArticleSelected: (String) -> Unit,
    onBack: () -> Unit,
    onErrorDismiss: () -> Unit,
    navHost: @Composable (Modifier, Boolean) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val isWide = maxWidth >= WideLayoutMinWidth
        if (isWide) {
            ReaderWideLayout(
                state = state,
                articleState = articleState,
                selectedTopLevel = selectedTopLevel,
                isArticleRoute = isArticleRoute,
                onRefresh = onRefresh,
                onSearchChanged = onSearchChanged,
                onDestinationSelected = onDestinationSelected,
                onTopLevelSelected = onTopLevelSelected,
                onCloseArticle = onCloseArticle,
                onHubSelected = onHubSelected,
                onHubFeedRequested = onHubFeedRequested,
                onTagSelected = onTagSelected,
                onFavoriteHubToggled = onFavoriteHubToggled,
                onFavoriteTagToggled = onFavoriteTagToggled,
                onArticleBookmarkToggled = onArticleBookmarkToggled,
                onRelatedArticleSelected = onRelatedArticleSelected,
                onErrorDismiss = onErrorDismiss,
                navHost = navHost,
            )
        } else {
            ReaderMobileLayout(
                state = state,
                selectedTopLevel = selectedTopLevel,
                isArticleRoute = isArticleRoute,
                onRefresh = onRefresh,
                onSearchChanged = onSearchChanged,
                onDestinationSelected = onDestinationSelected,
                onTopLevelSelected = onTopLevelSelected,
                onBack = onBack,
                onErrorDismiss = onErrorDismiss,
                navHost = navHost,
            )
        }
    }
}

@Composable
private fun ReaderWideLayout(
    state: ReaderUiState,
    articleState: ArticleUiState,
    selectedTopLevel: Screen,
    isArticleRoute: Boolean,
    onRefresh: () -> Unit,
    onSearchChanged: (String) -> Unit,
    onDestinationSelected: (ReaderDestination) -> Unit,
    onTopLevelSelected: (Screen) -> Unit,
    onCloseArticle: () -> Unit,
    onHubSelected: (String?) -> Unit,
    onHubFeedRequested: (String, String) -> Unit,
    onTagSelected: (String?) -> Unit,
    onFavoriteHubToggled: (String) -> Unit,
    onFavoriteTagToggled: (String) -> Unit,
    onArticleBookmarkToggled: () -> Unit,
    onRelatedArticleSelected: (String) -> Unit,
    onErrorDismiss: () -> Unit,
    navHost: @Composable (Modifier, Boolean) -> Unit,
) {
    Row(Modifier.fillMaxSize()) {
        ReaderRail(
            selectedTopLevel = selectedTopLevel,
            onDestinationSelected = onDestinationSelected,
            onScreenSelected = onTopLevelSelected,
        )
        Column(modifier = Modifier.weight(1f)) {
            if (!isArticleRoute) {
                ReaderTopBar(
                    state = state,
                    onRefresh = onRefresh,
                    onSearchChanged = onSearchChanged
                )
            }
            ReaderErrorBanner(
                message = state.errorMessage,
                onRefresh = onRefresh,
                onDismiss = onErrorDismiss,
            )
            navHost(Modifier.weight(1f), true)
        }
        val article = articleState.article
        val articleError = articleState.errorMessage
        if (state.isArticleOpen) {
            VerticalDivider(Modifier.fillMaxHeight())
            when {
                article != null -> ArticleScreen(
                    modifier = Modifier.weight(1f),
                    article = article,
                    showBack = false,
                    settings = state.settings,
                    favoriteTagIds = state.favoriteTagIds,
                    favoriteHubIds = state.favoriteHubIds,
                    onBack = onCloseArticle,
                    onHubSelected = { hubId -> onHubSelected(hubId) },
                    onHubFeedRequested = onHubFeedRequested,
                    onFavoriteHubToggled = onFavoriteHubToggled,
                    onTagSelected = onTagSelected,
                    onFavoriteTagToggled = onFavoriteTagToggled,
                    isBookmarked = articleState.isBookmarked,
                    onBookmark = onArticleBookmarkToggled,
                    comments = articleState.comments,
                    relatedArticles = articleState.relatedArticles,
                    isLoadingExtras = articleState.isLoadingExtras,
                    onRelatedArticleSelected = onRelatedArticleSelected,
                )

                articleError != null -> Box(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(articleError)
                }

                else -> Box(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun ReaderMobileLayout(
    state: ReaderUiState,
    selectedTopLevel: Screen,
    isArticleRoute: Boolean,
    onRefresh: () -> Unit,
    onSearchChanged: (String) -> Unit,
    onDestinationSelected: (ReaderDestination) -> Unit,
    onTopLevelSelected: (Screen) -> Unit,
    onBack: () -> Unit,
    onErrorDismiss: () -> Unit,
    navHost: @Composable (Modifier, Boolean) -> Unit,
) {
    PlatformBackHandler(enabled = isArticleRoute, onBack = onBack)
    Scaffold(
        bottomBar = {
            if (!isArticleRoute) {
                ReaderBottomBar(
                    selectedTopLevel = selectedTopLevel,
                    onDestinationSelected = onDestinationSelected,
                    onScreenSelected = onTopLevelSelected,
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            if (!isArticleRoute) {
                ReaderTopBar(
                    state = state,
                    onRefresh = onRefresh,
                    onSearchChanged = onSearchChanged
                )
            }
            ReaderErrorBanner(
                message = state.errorMessage,
                onRefresh = onRefresh,
                onDismiss = onErrorDismiss,
            )
            navHost(Modifier.weight(1f), false)
        }
    }
}

@Composable
private fun ReaderErrorBanner(
    message: String?,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (message != null) {
        ErrorBanner(
            message = message,
            onRetry = onRefresh,
            onDismiss = onDismiss,
        )
    }
}

@Composable
internal fun AppNavHost(
    backStack: List<NavKey>,
    state: ReaderUiState,
    viewModel: FeedViewModel,
    articleViewModel: ArticleViewModel,
    openArticle: (String) -> Unit,
    isWide: Boolean,
    onBack: () -> Unit,
    navigateToFeed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val route = backStack.lastOrNull() as? Screen ?: Screen.Feed
    Box(modifier.fillMaxSize()) {
        when (route) {
            Screen.Feed -> FeedRoute(state, viewModel, openArticle, isWide)
            Screen.Bookmarks -> BookmarksRoute(state, viewModel, openArticle, isWide)
            Screen.Search -> SearchRoute(state, viewModel, openArticle)
            Screen.Sources -> SourcesRoute(state, viewModel, navigateToFeed)
            Screen.Settings -> SettingsRoute(state, viewModel)
            is Screen.Article -> ArticleRoute(
                route = route,
                state = state,
                viewModel = viewModel,
                articleViewModel = articleViewModel,
                onBack = onBack,
                navigateToFeed = navigateToFeed,
            )
        }
    }
}

@Composable
private fun FeedRoute(
    state: ReaderUiState,
    viewModel: FeedViewModel,
    openArticle: (String) -> Unit,
    isWide: Boolean,
) {
    FeedScreen(
        isWide = isWide,
        state = state,
        onHubSelected = { viewModel.dispatch(FeedIntent.SelectHub(it)) },
        onFeedSelected = { viewModel.dispatch(FeedIntent.SelectFeed(it)) },
        onTagSelected = { viewModel.dispatch(FeedIntent.SelectTag(it)) },
        onClearFilters = { viewModel.dispatch(FeedIntent.ClearFilters) },
        onArticleSelected = openArticle,
        onBookmark = { viewModel.dispatch(FeedIntent.ToggleArticleBookmark(it)) },
        isRefreshing = state.isRefreshing,
        onRefresh = { viewModel.dispatch(FeedIntent.Refresh) },
        onLoadMore = { viewModel.dispatch(FeedIntent.LoadMore) },
    )
}

@Composable
private fun BookmarksRoute(
    state: ReaderUiState,
    viewModel: FeedViewModel,
    openArticle: (String) -> Unit,
    isWide: Boolean,
) {
    FeedScreen(
        isWide = isWide,
        state = state,
        onHubSelected = { viewModel.dispatch(FeedIntent.SelectHub(it)) },
        onFeedSelected = { viewModel.dispatch(FeedIntent.SelectFeed(it)) },
        onTagSelected = { viewModel.dispatch(FeedIntent.SelectTag(it)) },
        onClearFilters = { viewModel.dispatch(FeedIntent.ClearFilters) },
        onArticleSelected = openArticle,
        onBookmark = { viewModel.dispatch(FeedIntent.ToggleArticleBookmark(it)) },
        isRefreshing = state.isRefreshing,
        onRefresh = { viewModel.dispatch(FeedIntent.Refresh) },
        onLoadMore = { viewModel.dispatch(FeedIntent.LoadMore) },
    )
}

@Composable
private fun SearchRoute(
    state: ReaderUiState,
    viewModel: FeedViewModel,
    openArticle: (String) -> Unit,
) {
    SearchScreen(
        state = state,
        onSearchChanged = { viewModel.dispatch(FeedIntent.UpdateSearchQuery(it)) },
        onArticleSelected = openArticle,
        onBookmark = { viewModel.dispatch(FeedIntent.ToggleArticleBookmark(it)) },
        isRefreshing = state.isRefreshing,
        onRefresh = { viewModel.dispatch(FeedIntent.Refresh) },
    )
}

@Composable
private fun SourcesRoute(
    state: ReaderUiState,
    viewModel: FeedViewModel,
    navigateToFeed: () -> Unit,
) {
    SourceScreen(
        feeds = state.feeds,
        activeFeedId = state.activeFeedId,
        favoriteHubs = state.favoriteHubs,
        favoriteTags = state.favoriteTags,
        onFeedSelected = { feedId ->
            navigateToFeed()
            viewModel.dispatch(FeedIntent.SelectFeed(feedId))
        },
        onCustomFeedSaved = { id, title, url ->
            viewModel.dispatch(
                FeedIntent.SaveCustomFeed(
                    id,
                    title,
                    url
                )
            )
        },
        onCustomFeedRemoved = { viewModel.dispatch(FeedIntent.RemoveCustomFeed(it)) },
        onFavoriteHubRemoved = { viewModel.dispatch(FeedIntent.ToggleFavoriteHub(it)) },
        onFavoriteTagRemoved = { viewModel.dispatch(FeedIntent.ToggleFavoriteTag(it)) },
    )
}

@Composable
private fun SettingsRoute(
    state: ReaderUiState,
    viewModel: FeedViewModel,
) {
    SettingsScreen(
        state = state,
        onCardModeChanged = { viewModel.dispatch(FeedIntent.SetFeedCardMode(it)) },
        onFontScaleChanged = { value ->
            viewModel.dispatch(FeedIntent.UpdateSettings {
                it.copy(
                    fontScale = value
                )
            })
        },
        onLineHeightChanged = { value ->
            viewModel.dispatch(FeedIntent.UpdateSettings {
                it.copy(
                    lineHeightScale = value
                )
            })
        },
        onThemeModeChanged = { value ->
            viewModel.dispatch(FeedIntent.UpdateSettings {
                it.copy(
                    themeMode = value
                )
            })
        },
        onOpenLinksInsideChanged = { value ->
            viewModel.dispatch(FeedIntent.UpdateSettings {
                it.copy(
                    openLinksInsideApp = value
                )
            })
        },
        onFavoriteHubToggled = { viewModel.dispatch(FeedIntent.ToggleFavoriteHub(it)) },
        onFavoriteTagToggled = { viewModel.dispatch(FeedIntent.ToggleFavoriteTag(it)) },
    )
}

@Composable
private fun ArticleRoute(
    route: Screen.Article,
    state: ReaderUiState,
    viewModel: FeedViewModel,
    articleViewModel: ArticleViewModel,
    onBack: () -> Unit,
    navigateToFeed: () -> Unit,
) {
    LaunchedEffect(route.articleId) {
        viewModel.dispatch(FeedIntent.SelectArticle(route.articleId))
        articleViewModel.dispatch(ArticleIntent.Open(route.articleId))
    }

    val articleState by articleViewModel.state.collectAsState()
    val article = articleState.article
    val articleError = articleState.errorMessage
    if (articleError != null && article == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(articleError)
        }
    } else if (article == null || articleState.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        ArticleScreen(
            modifier = Modifier.fillMaxSize(),
            article = article,
            showBack = true,
            settings = state.settings,
            favoriteTagIds = state.favoriteTagIds,
            favoriteHubIds = state.favoriteHubIds,
            onBack = onBack,
            onHubSelected = { hubId ->
                viewModel.dispatch(FeedIntent.SelectHub(hubId))
                navigateToFeed()
            },
            onHubFeedRequested = { slug, title ->
                viewModel.dispatch(FeedIntent.OpenHubFeed(slug, title))
                navigateToFeed()
            },
            onFavoriteHubToggled = { viewModel.dispatch(FeedIntent.ToggleFavoriteHub(it)) },
            onTagSelected = { tagId ->
                viewModel.dispatch(FeedIntent.SelectTag(tagId))
                navigateToFeed()
            },
            onFavoriteTagToggled = { viewModel.dispatch(FeedIntent.ToggleFavoriteTag(it)) },
            isBookmarked = articleState.isBookmarked,
            onBookmark = { articleViewModel.dispatch(ArticleIntent.ToggleBookmark) },
            comments = articleState.comments,
            relatedArticles = articleState.relatedArticles,
            isLoadingExtras = articleState.isLoadingExtras,
            onRelatedArticleSelected = { articleId ->
                viewModel.dispatch(FeedIntent.SelectArticle(articleId))
                articleViewModel.dispatch(ArticleIntent.Open(articleId))
            },
        )
    }
}

@Preview
@Composable
private fun ReaderAppContentPreview() {
    MaterialTheme {
        ReaderAppContent(
            state = ReaderUiState(),
            articleState = ArticleUiState(),
            selectedTopLevel = Screen.Feed,
            isArticleRoute = false,
            onRefresh = {},
            onSearchChanged = {},
            onDestinationSelected = {},
            onTopLevelSelected = {},
            onCloseArticle = {},
            onHubSelected = {},
            onHubFeedRequested = { _, _ -> },
            onTagSelected = {},
            onFavoriteHubToggled = {},
            onFavoriteTagToggled = {},
            onArticleBookmarkToggled = {},
            onRelatedArticleSelected = {},
            onBack = {},
            onErrorDismiss = {},
            navHost = { modifier, _ ->
                Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Лента статей")
                }
            },
        )
    }
}

// Предполагается, что ReaderTheme определен в этом файле или импортирован
// @Composable private fun ReaderTheme(...) { ... }

@Preview(name = "Wide Layout", showBackground = true, device = "spec:width=1280dp,height=800dp")
@Composable
private fun ReaderAppContentWidePreview() {
    ReaderTheme(settings = FeedSettings.defaults()) {
        ReaderAppContent(
            state = ReaderUiState(isArticleOpen = true, selectedArticleId = "1"),
            articleState = ArticleUiState(),
            selectedTopLevel = Screen.Feed,
            isArticleRoute = false,
            onRefresh = {},
            onSearchChanged = {},
            onDestinationSelected = {},
            onTopLevelSelected = {},
            onCloseArticle = {},
            onHubSelected = {},
            onHubFeedRequested = { _, _ -> },
            onTagSelected = {},
            onFavoriteHubToggled = {},
            onFavoriteTagToggled = {},
            onArticleBookmarkToggled = {},
            onRelatedArticleSelected = {},
            onBack = {},
            onErrorDismiss = {},
            navHost = { modifier, wide ->
                Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (wide) "Широкий макет: Лента" else "Лента")
                }
            },
        )
    }
}

@Preview(name = "Mobile Article Open", showBackground = true, device = "spec:width=360dp,height=800dp")
@Composable
private fun ReaderAppContentArticlePreview() {
    ReaderTheme(settings = FeedSettings.defaults()) {
        ReaderAppContent(
            state = ReaderUiState(isArticleOpen = true, selectedArticleId = "1"),
            articleState = ArticleUiState(),
            selectedTopLevel = Screen.Feed,
            isArticleRoute = true,
            onRefresh = {},
            onSearchChanged = {},
            onDestinationSelected = {},
            onTopLevelSelected = {},
            onCloseArticle = {},
            onHubSelected = {},
            onHubFeedRequested = { _, _ -> },
            onTagSelected = {},
            onFavoriteHubToggled = {},
            onFavoriteTagToggled = {},
            onArticleBookmarkToggled = {},
            onRelatedArticleSelected = {},
            onBack = {},
            onErrorDismiss = {},
            navHost = { modifier, _ ->
                Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Статья открыта")
                }
            },
        )
    }
}

@Preview(name = "Error State", showBackground = true, device = "spec:width=360dp,height=800dp")
@Composable
private fun ReaderAppContentErrorPreview() {
    ReaderTheme(settings = FeedSettings.defaults()) {
        ReaderAppContent(
            state = ReaderUiState(errorMessage = "Не удалось загрузить ленту"),
            articleState = ArticleUiState(),
            selectedTopLevel = Screen.Feed,
            isArticleRoute = false,
            onRefresh = {},
            onSearchChanged = {},
            onDestinationSelected = {},
            onTopLevelSelected = {},
            onCloseArticle = {},
            onHubSelected = {},
            onHubFeedRequested = { _, _ -> },
            onTagSelected = {},
            onFavoriteHubToggled = {},
            onFavoriteTagToggled = {},
            onArticleBookmarkToggled = {},
            onRelatedArticleSelected = {},
            onBack = {},
            onErrorDismiss = {},
            navHost = { modifier, _ ->
                Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            },
        )
    }
}

@Preview(name = "Dark Theme", showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ReaderAppContentDarkPreview() {
    // Явно указываем Dark, чтобы ReaderTheme применил темную схему
    ReaderTheme(settings = FeedSettings.defaults().copy(themeMode = ThemeMode.Dark)) {
        ReaderAppContent(
            state = ReaderUiState(),
            articleState = ArticleUiState(),
            selectedTopLevel = Screen.Feed,
            isArticleRoute = false,
            onRefresh = {},
            onSearchChanged = {},
            onDestinationSelected = {},
            onTopLevelSelected = {},
            onCloseArticle = {},
            onHubSelected = {},
            onHubFeedRequested = { _, _ -> },
            onTagSelected = {},
            onFavoriteHubToggled = {},
            onFavoriteTagToggled = {},
            onArticleBookmarkToggled = {},
            onRelatedArticleSelected = {},
            onBack = {},
            onErrorDismiss = {},
            navHost = { modifier, _ ->
                Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Тёмная тема")
                }
            },
        )
    }
}

@Preview(name = "Large Font", showBackground = true, fontScale = 1.5f)
@Composable
private fun ReaderAppContentLargeFontPreview() {
    ReaderTheme(settings = FeedSettings.defaults()) {
        ReaderAppContent(
            state = ReaderUiState(),
            articleState = ArticleUiState(),
            selectedTopLevel = Screen.Feed,
            isArticleRoute = false,
            onRefresh = {},
            onSearchChanged = {},
            onDestinationSelected = {},
            onTopLevelSelected = {},
            onCloseArticle = {},
            onHubSelected = {},
            onHubFeedRequested = { _, _ -> },
            onTagSelected = {},
            onFavoriteHubToggled = {},
            onFavoriteTagToggled = {},
            onArticleBookmarkToggled = {},
            onRelatedArticleSelected = {},
            onBack = {},
            onErrorDismiss = {},
            navHost = { modifier, _ ->
                Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Увеличенный шрифт")
                }
            },
        )
    }
}


