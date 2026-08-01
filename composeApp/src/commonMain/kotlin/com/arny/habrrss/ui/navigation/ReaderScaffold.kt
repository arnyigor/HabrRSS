package com.arny.habrrss.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.arny.habrrss.navigation.Screen
import com.arny.habrrss.presentation.ArticleIntent
import com.arny.habrrss.presentation.ArticleUiState
import com.arny.habrrss.presentation.ArticleViewModel
import com.arny.habrrss.presentation.FeedViewModel
import com.arny.habrrss.presentation.ReaderDestination
import com.arny.habrrss.presentation.ReaderUiState
import com.arny.habrrss.ui.article.ArticleScreen
import com.arny.habrrss.ui.components.ErrorBanner
import com.arny.habrrss.ui.components.WideLayoutMinWidth
import com.arny.habrrss.ui.feed.FeedScreen
import com.arny.habrrss.ui.search.SearchScreen
import com.arny.habrrss.ui.settings.SettingsScreen
import com.arny.habrrss.ui.sources.SourceScreen
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/** Stateful entry: owns Navigation 3 back stack and connects ViewModels to stateless UI. */
@Composable
internal fun ReaderApp(
    state: ReaderUiState,
    viewModel: FeedViewModel,
    articleViewModel: ArticleViewModel,
) {
    val navBackStack = rememberNavBackStack(
        configuration = remember {
            SavedStateConfiguration {
                serializersModule = navigationSerializersModule
            }
        },
        Screen.Feed,
    )
    val savedRoute = navBackStack.lastOrNull() as? Screen ?: Screen.Feed
    val articleState by articleViewModel.state.collectAsState()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeContentPadding(),
    ) {
        val isWide = maxWidth >= WideLayoutMinWidth
        val currentRoute = if (isWide && savedRoute is Screen.Article) Screen.Feed else savedRoute
        val isArticleRoute = !isWide && currentRoute is Screen.Article
        val navigateToTopLevel: (Screen) -> Unit = { screen ->
            navBackStack.clear()
            navBackStack.add(screen)
            viewModel.closeArticle()
            articleViewModel.close()
        }
        val popBackStack: () -> Unit = {
            if (navBackStack.size > 1) {
                val removed = navBackStack.removeAt(navBackStack.lastIndex)
                if (removed is Screen.Article) {
                    viewModel.closeArticle()
                    articleViewModel.close()
                }
            }
        }
        val openArticle: (String) -> Unit = { articleId ->
            viewModel.loadArticleInPane(articleId)
            articleViewModel.openArticle(articleId)
            if (!isWide) {
                val articleRoute = Screen.Article(articleId)
                if (navBackStack.lastOrNull() != articleRoute) {
                    navBackStack.add(articleRoute)
                }
            }
        }
        val navigateToFeed: () -> Unit = {
            viewModel.selectDestination(Screen.Feed.toDestination())
            navigateToTopLevel(Screen.Feed)
        }

        LaunchedEffect(isWide, state.isArticleOpen, state.selectedArticleId) {
            val articleId = state.selectedArticleId
            if (!isWide && state.isArticleOpen && articleId != null) {
                val articleRoute = Screen.Article(articleId)
                if (navBackStack.lastOrNull() != articleRoute) {
                    navBackStack.add(articleRoute)
                }
            }
        }

        ReaderAppContent(
            state = state,
            articleState = articleState,
            currentRoute = currentRoute,
            isArticleRoute = isArticleRoute,
            onRefresh = viewModel::refresh,
            onDestinationSelected = viewModel::selectDestination,
            onTopLevelSelected = navigateToTopLevel,
            onCloseArticle = {
                viewModel.closeArticle()
                articleViewModel.close()
            },
            onHubSelected = { hubId ->
                viewModel.selectHub(hubId)
                navigateToFeed()
            },
            onTagSelected = { tagId ->
                viewModel.selectTag(tagId)
                navigateToFeed()
            },
            onFavoriteHubToggled = viewModel::toggleFavoriteHub,
            onFavoriteTagToggled = viewModel::toggleFavoriteTag,
            onArticleBookmarkToggled = { articleViewModel.dispatch(ArticleIntent.ToggleBookmark) },
            navHost = { modifier, wide ->
                AppNavHost(
                    backStack = if (wide) listOf(currentRoute) else navBackStack,
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
    currentRoute: Screen?,
    isArticleRoute: Boolean,
    onRefresh: () -> Unit,
    onDestinationSelected: (ReaderDestination) -> Unit,
    onTopLevelSelected: (Screen) -> Unit,
    onCloseArticle: () -> Unit,
    onHubSelected: (String?) -> Unit,
    onTagSelected: (String?) -> Unit,
    onFavoriteHubToggled: (String) -> Unit,
    onFavoriteTagToggled: (String) -> Unit,
    onArticleBookmarkToggled: () -> Unit,
    navHost: @Composable (Modifier, Boolean) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val isWide = maxWidth >= WideLayoutMinWidth
        if (isWide) {
            ReaderWideLayout(
                state = state,
                articleState = articleState,
                currentRoute = currentRoute,
                isArticleRoute = isArticleRoute,
                onRefresh = onRefresh,
                onDestinationSelected = onDestinationSelected,
                onTopLevelSelected = onTopLevelSelected,
                onCloseArticle = onCloseArticle,
                onHubSelected = onHubSelected,
                onTagSelected = onTagSelected,
                onFavoriteHubToggled = onFavoriteHubToggled,
                onFavoriteTagToggled = onFavoriteTagToggled,
                onArticleBookmarkToggled = onArticleBookmarkToggled,
                navHost = navHost,
            )
        } else {
            ReaderMobileLayout(
                state = state,
                currentRoute = currentRoute,
                isArticleRoute = isArticleRoute,
                onRefresh = onRefresh,
                onDestinationSelected = onDestinationSelected,
                onTopLevelSelected = onTopLevelSelected,
                navHost = navHost,
            )
        }
    }
}

@Composable
private fun ReaderWideLayout(
    state: ReaderUiState,
    articleState: ArticleUiState,
    currentRoute: Screen?,
    isArticleRoute: Boolean,
    onRefresh: () -> Unit,
    onDestinationSelected: (ReaderDestination) -> Unit,
    onTopLevelSelected: (Screen) -> Unit,
    onCloseArticle: () -> Unit,
    onHubSelected: (String?) -> Unit,
    onTagSelected: (String?) -> Unit,
    onFavoriteHubToggled: (String) -> Unit,
    onFavoriteTagToggled: (String) -> Unit,
    onArticleBookmarkToggled: () -> Unit,
    navHost: @Composable (Modifier, Boolean) -> Unit,
) {
    Row(Modifier.fillMaxSize()) {
        ReaderRail(
            currentRoute = currentRoute,
            onDestinationSelected = onDestinationSelected,
            onScreenSelected = onTopLevelSelected,
        )
        Column(modifier = Modifier.weight(1f)) {
            if (!isArticleRoute) {
                ReaderTopBar(state = state, onRefresh = onRefresh)
            }
            state.errorMessage?.let { ErrorBanner(it, onRetry = onRefresh) }
            navHost(Modifier.weight(1f), true)
        }
        val article = articleState.article
        if (state.isArticleOpen && article != null) {
            VerticalDivider(Modifier.fillMaxHeight())
            ArticleScreen(
                modifier = Modifier.weight(1f),
                article = article,
                showBack = false,
                settings = state.settings,
                favoriteTagIds = state.favoriteTagIds,
                favoriteHubIds = state.favoriteHubIds,
                onBack = onCloseArticle,
                onHubSelected = onHubSelected,
                onFavoriteHubToggled = onFavoriteHubToggled,
                onTagSelected = onTagSelected,
                onFavoriteTagToggled = onFavoriteTagToggled,
                isBookmarked = articleState.isBookmarked,
                onBookmark = onArticleBookmarkToggled,
            )
        }
    }
}

@Composable
private fun ReaderMobileLayout(
    state: ReaderUiState,
    currentRoute: Screen?,
    isArticleRoute: Boolean,
    onRefresh: () -> Unit,
    onDestinationSelected: (ReaderDestination) -> Unit,
    onTopLevelSelected: (Screen) -> Unit,
    navHost: @Composable (Modifier, Boolean) -> Unit,
) {
    Scaffold(
        bottomBar = {
            if (!isArticleRoute) {
                ReaderBottomBar(
                    currentRoute = currentRoute,
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
                ReaderTopBar(state = state, onRefresh = onRefresh)
            }
            state.errorMessage?.let { ErrorBanner(it, onRetry = onRefresh) }
            navHost(Modifier.weight(1f), false)
        }
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
    NavDisplay(
        backStack = backStack,
        modifier = modifier.fillMaxSize(),
        onBack = onBack,
        transitionSpec = {
            (slideInHorizontally(
                initialOffsetX = { width -> width },
                animationSpec = tween(300),
            ) + fadeIn(animationSpec = tween(180)))
                .togetherWith(
                    slideOutHorizontally(
                        targetOffsetX = { width -> -width },
                        animationSpec = tween(300),
                    ) + fadeOut(animationSpec = tween(180)),
                )
        },
        popTransitionSpec = {
            (slideInHorizontally(
                initialOffsetX = { width -> -width },
                animationSpec = tween(300),
            ) + fadeIn(animationSpec = tween(180)))
                .togetherWith(
                    slideOutHorizontally(
                        targetOffsetX = { width -> width },
                        animationSpec = tween(300),
                    ) + fadeOut(animationSpec = tween(180)),
                )
        },
        entryProvider = entryProvider<NavKey> {
            entry<Screen.Feed> {
                FeedRoute(state, viewModel, openArticle, isWide)
            }

            entry<Screen.Bookmarks> {
                BookmarksRoute(state, viewModel, openArticle, isWide)
            }

            entry<Screen.Search> {
                SearchRoute(state, viewModel, openArticle)
            }

            entry<Screen.Sources> {
                SourcesRoute(state, viewModel, navigateToFeed)
            }

            entry<Screen.Settings> {
                SettingsRoute(state, viewModel)
            }

            entry<Screen.Article> { route ->
                ArticleRoute(
                    route = route,
                    state = state,
                    viewModel = viewModel,
                    articleViewModel = articleViewModel,
                    onBack = onBack,
                    navigateToFeed = navigateToFeed,
                )
            }
        },
    )
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
        onFeedSelected = viewModel::selectFeed,
        onPublicationSectionSelected = viewModel::selectPublicationSection,
        onArticleSelected = openArticle,
        onBookmark = viewModel::toggleArticleBookmark,
        onHubSelected = viewModel::selectHub,
        onFavoriteHubToggled = viewModel::toggleFavoriteHub,
        onTagSelected = viewModel::selectTag,
        onFavoriteTagToggled = viewModel::toggleFavoriteTag,
        onClearFilters = viewModel::clearFilters,
        onUnreadOnlyChanged = viewModel::setShowUnreadOnly,
        onCardModeChanged = viewModel::setFeedCardMode,
        onSortModeChanged = viewModel::setFeedSortMode,
        isRefreshing = state.isRefreshing,
        onRefresh = viewModel::refresh,
        onLoadMore = viewModel::loadMoreItems,
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
        onFeedSelected = viewModel::selectFeed,
        onPublicationSectionSelected = viewModel::selectPublicationSection,
        onArticleSelected = openArticle,
        onBookmark = viewModel::toggleArticleBookmark,
        onHubSelected = viewModel::selectHub,
        onFavoriteHubToggled = viewModel::toggleFavoriteHub,
        onTagSelected = viewModel::selectTag,
        onFavoriteTagToggled = viewModel::toggleFavoriteTag,
        onClearFilters = viewModel::clearFilters,
        onUnreadOnlyChanged = viewModel::setShowUnreadOnly,
        onCardModeChanged = viewModel::setFeedCardMode,
        onSortModeChanged = viewModel::setFeedSortMode,
        isRefreshing = state.isRefreshing,
        onRefresh = viewModel::refresh,
        onLoadMore = viewModel::loadMoreItems,
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
        onSearchChanged = viewModel::updateSearchQuery,
        onArticleSelected = openArticle,
        onBookmark = viewModel::toggleArticleBookmark,
        isRefreshing = state.isRefreshing,
        onRefresh = viewModel::refresh,
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
        onFeedSelected = { feedId ->
            viewModel.selectFeed(feedId)
            navigateToFeed()
        },
        onCustomFeedSaved = viewModel::saveCustomFeed,
        onCustomFeedRemoved = viewModel::removeCustomFeed,
    )
}

@Composable
private fun SettingsRoute(
    state: ReaderUiState,
    viewModel: FeedViewModel,
) {
    SettingsScreen(
        state = state,
        onCardModeChanged = viewModel::setFeedCardMode,
        onFontScaleChanged = { value -> viewModel.updateSettings { it.copy(fontScale = value) } },
        onLineHeightChanged = { value -> viewModel.updateSettings { it.copy(lineHeightScale = value) } },
        onOpenLinksInsideChanged = { value -> viewModel.updateSettings { it.copy(openLinksInsideApp = value) } },
        onFavoriteHubToggled = viewModel::toggleFavoriteHub,
        onFavoriteTagToggled = viewModel::toggleFavoriteTag,
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
        viewModel.loadArticleInPane(route.articleId)
        articleViewModel.openArticle(route.articleId)
    }

    val articleState by articleViewModel.state.collectAsState()
    val article = articleState.article
    if (article == null || articleState.isLoading) {
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
                viewModel.selectHub(hubId)
                navigateToFeed()
            },
            onFavoriteHubToggled = viewModel::toggleFavoriteHub,
            onTagSelected = { tagId ->
                viewModel.selectTag(tagId)
                navigateToFeed()
            },
            onFavoriteTagToggled = viewModel::toggleFavoriteTag,
            isBookmarked = articleState.isBookmarked,
            onBookmark = { articleViewModel.dispatch(ArticleIntent.ToggleBookmark) },
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
            currentRoute = Screen.Feed,
            isArticleRoute = false,
            onRefresh = {},
            onDestinationSelected = {},
            onTopLevelSelected = {},
            onCloseArticle = {},
            onHubSelected = {},
            onTagSelected = {},
            onFavoriteHubToggled = {},
            onFavoriteTagToggled = {},
            onArticleBookmarkToggled = {},
            navHost = { modifier, _ ->
                Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Лента статей")
                }
            },
        )
    }
}

private val navigationSerializersModule = SerializersModule {
    polymorphic(NavKey::class) {
        subclass(Screen.Feed::class, Screen.Feed.serializer())
        subclass(Screen.Sources::class, Screen.Sources.serializer())
        subclass(Screen.Bookmarks::class, Screen.Bookmarks.serializer())
        subclass(Screen.Search::class, Screen.Search.serializer())
        subclass(Screen.Settings::class, Screen.Settings.serializer())
        subclass(Screen.Article::class, Screen.Article.serializer())
    }
}
