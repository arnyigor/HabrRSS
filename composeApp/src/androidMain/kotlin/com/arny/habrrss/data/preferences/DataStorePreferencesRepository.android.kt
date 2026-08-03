package com.arny.habrrss.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.arny.habrrss.domain.models.FeedSettings
import com.arny.habrrss.domain.models.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class DataStorePreferencesRepository(
    context: Context
) : UserPreferencesRepository {

    private val prefs: SharedPreferences = context.getSharedPreferences("habr_rss_prefs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _preferences = MutableStateFlow(loadPreferences())
    private val _favoriteHubIds = MutableStateFlow(prefs.getStringSet("favorite_hub_ids", emptySet()).orEmpty())
    private val _favoriteTagIds = MutableStateFlow(prefs.getStringSet("favorite_tag_ids", emptySet()).orEmpty())
    private val _customFeeds = MutableStateFlow(loadCustomFeeds())

    private fun loadPreferences(): FeedSettings {
        return FeedSettings(
            themeMode = try { ThemeMode.valueOf(prefs.getString("theme_mode", "System") ?: "System") } catch (e: Exception) { ThemeMode.System },
            fontScale = prefs.getFloat("font_scale", 1f),
            lineHeightScale = prefs.getFloat("line_height_scale", 1.25f),
            compactCards = prefs.getBoolean("compact_cards", false),
            offlinePolicy = FeedSettings.defaults().offlinePolicy,
            cacheSizeMb = FeedSettings.defaults().cacheSizeMb,
            autoRefreshMinutes = FeedSettings.defaults().autoRefreshMinutes,
            openLinksInsideApp = prefs.getBoolean("open_links_inside_app", false),
            feedCardMode = prefs.getString("feed_card_mode", null)
                ?: if (prefs.getBoolean("compact_cards", false)) "CompactText" else "Comfortable",
        )
    }

    private fun loadCustomFeeds(): List<CustomFeedPreference> {
        val raw = prefs.getString("custom_feeds", null) ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(CustomFeedPreference.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    override fun preferences(): Flow<FeedSettings> = _preferences.asStateFlow()

    override fun favoriteHubIds(): Flow<Set<String>> = _favoriteHubIds.asStateFlow()

    override fun favoriteTagIds(): Flow<Set<String>> = _favoriteTagIds.asStateFlow()

    override fun customFeeds(): Flow<List<CustomFeedPreference>> = _customFeeds.asStateFlow()

    override suspend fun setFontScale(scale: Float) {
        prefs.edit().putFloat("font_scale", scale).apply()
        _preferences.value = _preferences.value.copy(fontScale = scale)
    }

    override suspend fun setLineHeightScale(scale: Float) {
        prefs.edit().putFloat("line_height_scale", scale).apply()
        _preferences.value = _preferences.value.copy(lineHeightScale = scale)
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
        _preferences.value = _preferences.value.copy(themeMode = mode)
    }

    override suspend fun setCompactCards(enabled: Boolean) {
        prefs.edit().putBoolean("compact_cards", enabled).apply()
        _preferences.value = _preferences.value.copy(compactCards = enabled)
    }

    override suspend fun setFeedCardMode(mode: String) {
        prefs.edit().putString("feed_card_mode", mode).apply()
        _preferences.value = _preferences.value.copy(feedCardMode = mode)
    }

    override suspend fun setOpenLinksInsideApp(enabled: Boolean) {
        prefs.edit().putBoolean("open_links_inside_app", enabled).apply()
        _preferences.value = _preferences.value.copy(openLinksInsideApp = enabled)
    }

    override suspend fun setFavoriteHubIds(ids: Set<String>) {
        prefs.edit().putStringSet("favorite_hub_ids", ids).apply()
        _favoriteHubIds.value = ids
    }

    override suspend fun setFavoriteTagIds(ids: Set<String>) {
        prefs.edit().putStringSet("favorite_tag_ids", ids).apply()
        _favoriteTagIds.value = ids
    }

    override suspend fun upsertCustomFeed(feed: CustomFeedPreference) {
        val feeds = _customFeeds.value.filterNot { it.id == feed.id } + feed
        saveCustomFeeds(feeds)
    }

    override suspend fun removeCustomFeed(id: String) {
        saveCustomFeeds(_customFeeds.value.filterNot { it.id == id })
    }

    private fun saveCustomFeeds(feeds: List<CustomFeedPreference>) {
        prefs.edit().putString("custom_feeds", json.encodeToString(feeds)).apply()
        _customFeeds.value = feeds
    }

    override suspend fun clear() {
        prefs.edit().clear().apply()
        _preferences.value = FeedSettings.defaults()
        _favoriteHubIds.value = emptySet()
        _favoriteTagIds.value = emptySet()
        _customFeeds.value = emptyList()
    }
}
