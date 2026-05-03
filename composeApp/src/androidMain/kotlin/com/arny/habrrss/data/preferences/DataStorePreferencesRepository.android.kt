package com.arny.habrrss.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.arny.habrrss.domain.models.FeedSettings
import com.arny.habrrss.domain.models.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class DataStorePreferencesRepository(
    context: Context
) : UserPreferencesRepository {

    private val prefs: SharedPreferences = context.getSharedPreferences("habr_rss_prefs", Context.MODE_PRIVATE)

    private val _preferences = MutableStateFlow(loadPreferences())

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
        )
    }

    override fun preferences(): Flow<FeedSettings> = _preferences.asStateFlow()

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

    override suspend fun setOpenLinksInsideApp(enabled: Boolean) {
        prefs.edit().putBoolean("open_links_inside_app", enabled).apply()
        _preferences.value = _preferences.value.copy(openLinksInsideApp = enabled)
    }

    override suspend fun setFavoriteHubIds(ids: Set<String>) {
        prefs.edit().putStringSet("favorite_hub_ids", ids).apply()
    }

    override suspend fun setFavoriteTagIds(ids: Set<String>) {
        prefs.edit().putStringSet("favorite_tag_ids", ids).apply()
    }

    override suspend fun clear() {
        prefs.edit().clear().apply()
        _preferences.value = FeedSettings.defaults()
    }

    private fun SharedPreferences.edit(action: SharedPreferences.Editor.() -> Unit) {
        edit().apply(action)
    }
}