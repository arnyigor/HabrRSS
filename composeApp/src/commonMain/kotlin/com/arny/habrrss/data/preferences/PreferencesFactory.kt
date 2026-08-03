package com.arny.habrrss.data.preferences

import com.arny.habrrss.domain.models.FeedSettings
import com.arny.habrrss.domain.models.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Platform-specific factory for UserPreferencesRepository
 */
expect fun createUserPreferencesRepository(): UserPreferencesRepository

/**
 * Default implementation that returns a basic in-memory repository
 * Used when platform-specific implementation is not available
 */
class DefaultPreferencesRepository : UserPreferencesRepository {
    private val _preferences = MutableStateFlow(FeedSettings.defaults())
    private val _favoriteHubIds = MutableStateFlow(emptySet<String>())
    private val _favoriteTagIds = MutableStateFlow(emptySet<String>())
    private val _customFeeds = MutableStateFlow(emptyList<CustomFeedPreference>())

    override fun preferences(): Flow<FeedSettings> = _preferences

    override fun favoriteHubIds(): Flow<Set<String>> = _favoriteHubIds

    override fun favoriteTagIds(): Flow<Set<String>> = _favoriteTagIds

    override fun customFeeds(): Flow<List<CustomFeedPreference>> = _customFeeds

    override suspend fun setFontScale(scale: Float) {
        _preferences.value = _preferences.value.copy(fontScale = scale)
    }

    override suspend fun setLineHeightScale(scale: Float) {
        _preferences.value = _preferences.value.copy(lineHeightScale = scale)
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        _preferences.value = _preferences.value.copy(themeMode = mode)
    }

    override suspend fun setCompactCards(enabled: Boolean) {
        _preferences.value = _preferences.value.copy(compactCards = enabled)
    }

    override suspend fun setFeedCardMode(mode: String) {
        _preferences.value = _preferences.value.copy(feedCardMode = mode)
    }

    override suspend fun setOpenLinksInsideApp(enabled: Boolean) {
        _preferences.value = _preferences.value.copy(openLinksInsideApp = enabled)
    }

    override suspend fun setFavoriteHubIds(ids: Set<String>) {
        _favoriteHubIds.value = ids
    }

    override suspend fun setFavoriteTagIds(ids: Set<String>) {
        _favoriteTagIds.value = ids
    }

    override suspend fun upsertCustomFeed(feed: CustomFeedPreference) {
        _customFeeds.value = _customFeeds.value.filterNot { it.id == feed.id } + feed
    }

    override suspend fun removeCustomFeed(id: String) {
        _customFeeds.value = _customFeeds.value.filterNot { it.id == id }
    }

    override suspend fun clear() {
        _preferences.value = FeedSettings.defaults()
        _favoriteHubIds.value = emptySet()
        _favoriteTagIds.value = emptySet()
        _customFeeds.value = emptyList()
    }
}
