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

    private val _data = MutableStateFlow(loadData())
    private val _preferences = MutableStateFlow(_data.value.toFeedSettings())
    private val _favoriteHubIds = MutableStateFlow(_data.value.favoriteHubIds)
    private val _favoriteTagIds = MutableStateFlow(_data.value.favoriteTagIds)
    private val _customFeeds = MutableStateFlow(_data.value.customFeeds)

    private fun loadData(): PreferencesData {
        if (!file.exists()) return PreferencesData()
        return try {
            json.decodeFromString<PreferencesData>(file.readText())
        } catch (e: Exception) {
            PreferencesData()
        }
    }

    private fun saveData(data: PreferencesData) {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(data))
        _data.value = data
        _preferences.value = data.toFeedSettings()
        _favoriteHubIds.value = data.favoriteHubIds
        _favoriteTagIds.value = data.favoriteTagIds
        _customFeeds.value = data.customFeeds
    }

    private fun savePreferences(settings: FeedSettings) {
        saveData(
            _data.value.copy(
                themeMode = settings.themeMode.name,
                fontScale = settings.fontScale,
                lineHeightScale = settings.lineHeightScale,
                compactCards = settings.compactCards,
                openLinksInsideApp = settings.openLinksInsideApp,
            )
        )
    }

    override fun preferences(): Flow<FeedSettings> = _preferences.asStateFlow()

    override fun favoriteHubIds(): Flow<Set<String>> = _favoriteHubIds.asStateFlow()

    override fun favoriteTagIds(): Flow<Set<String>> = _favoriteTagIds.asStateFlow()

    override fun customFeeds(): Flow<List<CustomFeedPreference>> = _customFeeds.asStateFlow()

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
        saveData(_data.value.copy(favoriteHubIds = ids))
    }

    override suspend fun setFavoriteTagIds(ids: Set<String>) {
        saveData(_data.value.copy(favoriteTagIds = ids))
    }

    override suspend fun upsertCustomFeed(feed: CustomFeedPreference) {
        saveData(_data.value.copy(customFeeds = _customFeeds.value.filterNot { it.id == feed.id } + feed))
    }

    override suspend fun removeCustomFeed(id: String) {
        saveData(_data.value.copy(customFeeds = _customFeeds.value.filterNot { it.id == id }))
    }

    override suspend fun clear() {
        file.delete()
        saveData(PreferencesData())
    }

    @kotlinx.serialization.Serializable
    private data class PreferencesData(
        val themeMode: String = "System",
        val fontScale: Float = 1f,
        val lineHeightScale: Float = 1.25f,
        val compactCards: Boolean = false,
        val openLinksInsideApp: Boolean = false,
        val favoriteHubIds: Set<String> = emptySet(),
        val favoriteTagIds: Set<String> = emptySet(),
        val customFeeds: List<CustomFeedPreference> = emptyList(),
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
    }
}
