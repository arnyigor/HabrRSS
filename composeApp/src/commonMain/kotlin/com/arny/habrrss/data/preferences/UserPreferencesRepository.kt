package com.arny.habrrss.data.preferences

import com.arny.habrrss.domain.models.FeedSettings
import com.arny.habrrss.domain.models.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/**
 * Repository for persisting user preferences across app restarts.
 * Uses DataStore on Android and JSON file on Desktop.
 */
interface UserPreferencesRepository {
    /**
     * Observe current preferences as a Flow
     */
    fun preferences(): Flow<FeedSettings>

    fun favoriteHubIds(): Flow<Set<String>>

    fun favoriteTagIds(): Flow<Set<String>>

    fun customFeeds(): Flow<List<CustomFeedPreference>>

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
     * Update feed card display mode.
     */
    suspend fun setFeedCardMode(mode: String)

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

    suspend fun upsertCustomFeed(feed: CustomFeedPreference)

    suspend fun removeCustomFeed(id: String)

    /**
     * Clear all preferences
     */
    suspend fun clear()
}

@Serializable
data class CustomFeedPreference(
    val id: String,
    val title: String,
    val url: String,
)
