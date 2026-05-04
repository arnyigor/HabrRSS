package com.arny.habrrss.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Scaffold
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.arny.habrrss.navigation.Screen
import com.arny.habrrss.presentation.FeedViewModel
import com.arny.habrrss.presentation.ReaderUiState
import com.arny.habrrss.ui.article.ArticleScreen
import com.arny.habrrss.ui.components.ErrorBanner
import com.arny.habrrss.ui.components.PlatformBackHandler
import com.arny.habrrss.ui.components.WideLayoutMinWidth
import com.arny.habrrss.ui.feed.FeedScreen
import com.arny.habrrss.ui.search.SearchScreen
import com.arny.habrrss.ui.settings.SettingsScreen
import com.arny.habrrss.ui.sources.SourceScreen

@Composable
internal fun ReaderApp(
    state: ReaderUiState,
    viewModel: FeedViewModel,
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry.toScreen()
    val isArticleRoute = currentRoute is Screen.Article

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeContentPadding(),
    ) {
        val isWide = maxWidth >= WideLayoutMinWidth
        val openArticle: (String) -> Unit = { articleId ->
            viewModel.loadArticleInPane(articleId)
            if (!isWide) {
                navController.navigate(Screen.Article(articleId))
            }
        }

        PlatformBackHandler(enabled = !isWide && isArticleRoute) {
            if (isArticleRoute) {
                navController.popBackStack()
                viewModel.closeArticle()
            }
        }

        if (isWide) {
            ReaderWideLayout(
                state = state,
                viewModel = viewModel,
                navController = navController,
                currentRoute = currentRoute,
                isArticleRoute = isArticleRoute,
                openArticle = openArticle,
            )
        } else {
            ReaderMobileLayout(
                state = state,
                viewModel = viewModel,
                navController = navController,
                currentRoute = currentRoute,
                isArticleRoute = isArticleRoute,
                openArticle = openArticle,
            )
        }
    }
}

@Composable
private fun ReaderWideLayout(
    state: ReaderUiState,
    viewModel: FeedViewModel,
    navController: NavHostController,
    currentRoute: Screen?,
    isArticleRoute: Boolean,
    openArticle: (String) -> Unit,
) {
    Row(Modifier.fillMaxSize()) {
        ReaderRail(
            state = state,
            navController = navController,
            currentRoute = currentRoute,
            onDestinationSelected = viewModel::selectDestination,
        )
        Column(modifier = Modifier.weight(1f)) {
            if (!isArticleRoute) {
                ReaderTopBar(state = state, onRefresh = viewModel::refresh)
            }
            state.errorMessage?.let { ErrorBanner(it, onRetry = viewModel::refresh) }
            AppNavHost(
                navController = navController,
                state = state,
                viewModel = viewModel,
                openArticle = openArticle,
                isWide = true,
                modifier = Modifier.weight(1f),
            )
        }
        val article = state.article
        if (state.isArticleOpen && article != null) {
            VerticalDivider(Modifier.fillMaxHeight())
            ArticleScreen(
                modifier = Modifier.weight(1f),
                article = article,
                showBack = false,
                settings = state.settings,
                favoriteTagIds = state.favoriteTagIds,
                favoriteHubIds = state.favoriteHubIds,
                onBack = viewModel::closeArticle,
                onHubSelected = { hubId ->
                    viewModel.selectHub(hubId)
                    navController.navigateToFeed()
                },
                onFavoriteHubToggled = viewModel::toggleFavoriteHub,
                onTagSelected = { tagId ->
                    viewModel.selectTag(tagId)
                    navController.navigateToFeed()
                },
                onFavoriteTagToggled = viewModel::toggleFavoriteTag,
            )
        }
    }
}

@Composable
private fun ReaderMobileLayout(
    state: ReaderUiState,
    viewModel: FeedViewModel,
    navController: NavHostController,
    currentRoute: Screen?,
    isArticleRoute: Boolean,
    openArticle: (String) -> Unit,
) {
    Scaffold(
        bottomBar = {
            if (!isArticleRoute) {
                ReaderBottomBar(
                    navController = navController,
                    currentRoute = currentRoute,
                    onDestinationSelected = viewModel::selectDestination,
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
                ReaderTopBar(state = state, onRefresh = viewModel::refresh)
            }
            state.errorMessage?.let { ErrorBanner(it, onRetry = viewModel::refresh) }
            AppNavHost(
                navController = navController,
                state = state,
                viewModel = viewModel,
                openArticle = openArticle,
                isWide = false,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun NavBackStackEntry?.toScreen(): Screen? {
    val route = this?.destination?.route.orEmpty()
    return when {
        route.contains("Feed") -> Screen.Feed
        route.contains("Sources") -> Screen.Sources
        route.contains("Bookmarks") -> Screen.Bookmarks
        route.contains("Search") -> Screen.Search
        route.contains("Settings") -> Screen.Settings
        route.contains("Article") -> runCatching { this?.toRoute<Screen.Article>() }.getOrNull()
        else -> null
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    state: ReaderUiState,
    viewModel: FeedViewModel,
    openArticle: (String) -> Unit,
    isWide: Boolean,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Feed,
        modifier = modifier.fillMaxSize(),
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { width -> width },
                animationSpec = tween(300),
            )
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { width -> -width },
                animationSpec = tween(300),
            )
        },
        popEnterTransition = {
            slideInHorizontally(
                initialOffsetX = { width -> -width },
                animationSpec = tween(300),
            )
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { width -> width },
                animationSpec = tween(300),
            )
        },
    ) {
        composable<Screen.Feed>(
            enterTransition = { fadeIn(animationSpec = tween(180)) },
            exitTransition = { fadeOut(animationSpec = tween(180)) },
        ) {
            FeedRoute(state, viewModel, openArticle, isWide, navController)
        }

        composable<Screen.Bookmarks> {
            BookmarksRoute(state, viewModel, openArticle, isWide, navController)
        }

        composable<Screen.Search> {
            SearchRoute(state, viewModel, openArticle)
        }

        composable<Screen.Sources> {
            SourcesRoute(state, viewModel, navController)
        }

        composable<Screen.Settings> {
            SettingsRoute(state, viewModel)
        }

        composable<Screen.Article> {
            ArticleRoute(state, viewModel, navController)
        }
    }
}

@Composable
private fun FeedRoute(
    state: ReaderUiState,
    viewModel: FeedViewModel,
    openArticle: (String) -> Unit,
    isWide: Boolean,
    navController: NavHostController,
) {
    FeedScreen(
        isWide = isWide,
        state = state,
        onFeedSelected = viewModel::selectFeed,
        onPublicationSectionSelected = viewModel::selectPublicationSection,
        onArticleSelected = openArticle,
        onBookmark = viewModel::toggleArticleBookmark,
        onBack = viewModel::closeArticle,
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
    )
}

@Composable
private fun BookmarksRoute(
    state: ReaderUiState,
    viewModel: FeedViewModel,
    openArticle: (String) -> Unit,
    isWide: Boolean,
    navController: NavHostController,
) {
    FeedScreen(
        isWide = isWide,
        state = state,
        onFeedSelected = viewModel::selectFeed,
        onPublicationSectionSelected = viewModel::selectPublicationSection,
        onArticleSelected = openArticle,
        onBookmark = viewModel::toggleArticleBookmark,
        onBack = viewModel::closeArticle,
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
    navController: NavHostController,
) {
    SourceScreen(
        feeds = state.feeds,
        activeFeedId = state.activeFeedId,
        onFeedSelected = { feedId ->
            viewModel.selectFeed(feedId)
            navController.navigateToFeed()
        },
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
    state: ReaderUiState,
    viewModel: FeedViewModel,
    navController: NavHostController,
) {
    val article = state.article
    if (article == null) {
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
            onBack = {
                navController.popBackStack()
                viewModel.closeArticle()
            },
            onHubSelected = { hubId ->
                viewModel.selectHub(hubId)
                navController.navigateToFeed()
            },
            onFavoriteHubToggled = viewModel::toggleFavoriteHub,
            onTagSelected = { tagId ->
                viewModel.selectTag(tagId)
                navController.navigateToFeed()
            },
            onFavoriteTagToggled = viewModel::toggleFavoriteTag,
        )
    }
}

private fun NavHostController.navigateToFeed() {
    navigate(Screen.Feed) {
        popUpTo(graph.findStartDestination().id) {
            inclusive = false
        }
        launchSingleTop = true
    }
}
