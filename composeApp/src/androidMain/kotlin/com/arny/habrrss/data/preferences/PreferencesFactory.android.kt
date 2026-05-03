package com.arny.habrrss.data.preferences

import com.arny.habrrss.data.database.appContext

actual fun createUserPreferencesRepository(): UserPreferencesRepository {
    return DataStorePreferencesRepository(appContext)
}