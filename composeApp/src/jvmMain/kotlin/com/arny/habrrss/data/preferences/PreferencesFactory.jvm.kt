package com.arny.habrrss.data.preferences

import com.arny.habrrss.domain.models.FeedSettings
import com.arny.habrrss.domain.models.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

actual fun createUserPreferencesRepository(): UserPreferencesRepository {
    val prefsFolder = File(System.getProperty("user.home"), ".habrrss")
    val prefsFile = File(prefsFolder, "preferences.json")
    return FilePreferencesRepository(prefsFile)
}

class FilePreferencesRepository(
    private val file: File
) : UserPreferencesRepository {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private val _preferences = MutableStateFlow(loadPreferences())

    private fun loadPreferences(): FeedSettings {
        if (!file.exists()) return FeedSettings.defaults()
        return try {
            json.decodeFromString<PreferencesData>(file.readText()).toFeedSettings()
        } catch (e: Exception) {
            FeedSettings.defaults()
        }
    }

    private fun savePreferences(settings: FeedSettings) {
        file.parentFile?.mkdirs()
        val data = PreferencesData.fromFeedSettings(settings)
        file.writeText(json.encodeToString(data))
        _preferences.value = settings
    }

    override fun preferences(): Flow<FeedSettings> = _preferences.asStateFlow()

    override suspend fun setFontScale(scale: Float) {
        savePreferences(_preferences.value.copy(fontScale = scale))
    }

    override suspend fun setLineHeightScale(scale: Float) {
        savePreferences(_preferences.value.copy(lineHeightScale = scale))
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        savePreferences(_preferences.value.copy(themeMode = mode))
    }

    override suspend fun setCompactCards(enabled: Boolean) {
        savePreferences(_preferences.value.copy(compactCards = enabled))
    }

    override suspend fun setOpenLinksInsideApp(enabled: Boolean) {
        savePreferences(_preferences.value.copy(openLinksInsideApp = enabled))
    }

    override suspend fun setFavoriteHubIds(ids: Set<String>) {
        // Store in file path - for now just save to main file
        savePreferences(_preferences.value)
    }

    override suspend fun setFavoriteTagIds(ids: Set<String>) {
        savePreferences(_preferences.value)
    }

    override suspend fun clear() {
        file.delete()
        _preferences.value = FeedSettings.defaults()
    }

    @kotlinx.serialization.Serializable
    private data class PreferencesData(
        val themeMode: String = "System",
        val fontScale: Float = 1f,
        val lineHeightScale: Float = 1.25f,
        val compactCards: Boolean = false,
        val openLinksInsideApp: Boolean = false,
    ) {
        fun toFeedSettings(): FeedSettings = FeedSettings(
            themeMode = try { ThemeMode.valueOf(themeMode) } catch (e: Exception) { ThemeMode.System },
            fontScale = fontScale,
            lineHeightScale = lineHeightScale,
            compactCards = compactCards,
            offlinePolicy = FeedSettings.defaults().offlinePolicy,
            cacheSizeMb = FeedSettings.defaults().cacheSizeMb,
            autoRefreshMinutes = FeedSettings.defaults().autoRefreshMinutes,
            openLinksInsideApp = openLinksInsideApp,
        )

        companion object {
            fun fromFeedSettings(settings: FeedSettings): PreferencesData = PreferencesData(
                themeMode = settings.themeMode.name,
                fontScale = settings.fontScale,
                lineHeightScale = settings.lineHeightScale,
                compactCards = settings.compactCards,
                openLinksInsideApp = settings.openLinksInsideApp,
            )
        }
    }
}