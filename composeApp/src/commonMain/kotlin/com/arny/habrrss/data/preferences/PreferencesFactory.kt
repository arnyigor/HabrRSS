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

    override fun preferences(): Flow<FeedSettings> = _preferences

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

    override suspend fun setOpenLinksInsideApp(enabled: Boolean) {
        _preferences.value = _preferences.value.copy(openLinksInsideApp = enabled)
    }

    override suspend fun setFavoriteHubIds(ids: Set<String>) {
        // Not implemented in default
    }

    override suspend fun setFavoriteTagIds(ids: Set<String>) {
        // Not implemented in default
    }

    override suspend fun clear() {
        _preferences.value = FeedSettings.defaults()
    }
}