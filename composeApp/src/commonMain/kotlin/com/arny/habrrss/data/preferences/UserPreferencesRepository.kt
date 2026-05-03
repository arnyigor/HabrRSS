package com.arny.habrrss.data.preferences

import com.arny.habrrss.domain.models.FeedSettings
import com.arny.habrrss.domain.models.ThemeMode
import kotlinx.coroutines.flow.Flow

/**
 * Repository for persisting user preferences across app restarts.
 * Uses DataStore on Android and JSON file on Desktop.
 */
interface UserPreferencesRepository {
    /**
     * Observe current preferences as a Flow
     */
    fun preferences(): Flow<FeedSettings>

    /**
     * Update font scale
     */
    suspend fun setFontScale(scale: Float)

    /**
     * Update line height scale
     */
    suspend fun setLineHeightScale(scale: Float)

    /**
     * Update theme mode
     */
    suspend fun setThemeMode(mode: ThemeMode)

    /**
     * Update compact cards setting
     */
    suspend fun setCompactCards(enabled: Boolean)

    /**
     * Update open links inside app setting
     */
    suspend fun setOpenLinksInsideApp(enabled: Boolean)

    /**
     * Update favorite hub IDs
     */
    suspend fun setFavoriteHubIds(ids: Set<String>)

    /**
     * Update favorite tag IDs
     */
    suspend fun setFavoriteTagIds(ids: Set<String>)

    /**
     * Clear all preferences
     */
    suspend fun clear()
}