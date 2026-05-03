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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.arny.habrrss.navigation.Screen
import com.arny.habrrss.presentation.ArticleState
import com.arny.habrrss.presentation.ArticleViewModel
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
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun ReaderApp(
    state: ReaderUiState,
    viewModel: FeedViewModel,
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry.toScreen()

    // Determine if we're on article screen
    val isArticleRoute = currentRoute is Screen.Article

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeContentPadding(),
    ) {
        val isWide = maxWidth >= WideLayoutMinWidth
        val openArticle: (String) -> Unit = { articleId ->
            if (!isWide) {
                navController.navigate(Screen.Article(articleId))
            } else {
                viewModel.loadArticleInPane(articleId)
            }
        }

        // Handle back press on mobile
        PlatformBackHandler(enabled = !isWide && isArticleRoute) {
            if (isArticleRoute) {
                navController.popBackStack()
                viewModel.closeArticle()
            }
        }

        if (isWide) {
            // Desktop: Two-pane layout
            Row(Modifier.fillMaxSize()) {
                ReaderRail(
                    state = state,
                    navController = navController,
                    currentRoute = currentRoute,
                    onDestinationSelected = viewModel::selectDestination,
                )
                // Main content area with NavHost
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
                        modifier = Modifier.weight(1f)
                    )
                }
                // Article pane on the right
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
        } else {
            // Mobile: Stack with bottom bar
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
                        .fillMaxSize()
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
                        modifier = Modifier.weight(1f)
                    )
                }
            }
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
    navController: androidx.navigation.NavHostController,
    state: ReaderUiState,
    viewModel: FeedViewModel,
    openArticle: (String) -> Unit,
    isWide: Boolean,
    modifier: Modifier = Modifier
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
            FeedScreen(
                isWide = isWide,
                state = state,
                onFeedSelected = viewModel::selectFeed,
                onPublicationSectionSelected = viewModel::selectPublicationSection,
                onArticleSelected = openArticle,
                onBookmark = viewModel::toggleArticleBookmark,
                onBack = { navController.popBackStack() },
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

        composable<Screen.Bookmarks> {
            FeedScreen(
                isWide = isWide,
                state = state,
                onFeedSelected = viewModel::selectFeed,
                onPublicationSectionSelected = viewModel::selectPublicationSection,
                onArticleSelected = openArticle,
                onBookmark = viewModel::toggleArticleBookmark,
                onBack = { navController.popBackStack() },
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

        composable<Screen.Search> {
            SearchScreen(
                state = state,
                onSearchChanged = viewModel::updateSearchQuery,
                onArticleSelected = openArticle,
                onBookmark = viewModel::toggleArticleBookmark,
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::refresh,
            )
        }

        composable<Screen.Sources> {
            SourceScreen(
                feeds = state.feeds,
                activeFeedId = state.activeFeedId,
                onFeedSelected = { feedId ->
                    viewModel.selectFeed(feedId)
                    navController.navigateToFeed()
                },
            )
        }

        composable<Screen.Settings> {
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

        composable<Screen.Article> {
            val articleViewModel = koinViewModel<ArticleViewModel>()
            val articleState by articleViewModel.state.collectAsState()

            when (val current = articleState) {
                ArticleState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is ArticleState.Error -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        ErrorBanner(current.message)
                    }
                }
                is ArticleState.Success -> {
                    ArticleScreen(
                        modifier = Modifier.fillMaxSize(),
                        article = current.article,
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
        }
    }
}

private fun androidx.navigation.NavHostController.navigateToFeed() {
    navigate(Screen.Feed) {
        popUpTo(graph.findStartDestination().id) {
            inclusive = false
        }
        launchSingleTop = true
    }
}
