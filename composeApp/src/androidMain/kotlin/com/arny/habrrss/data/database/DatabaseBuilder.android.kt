package com.arny.habrrss.data.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

lateinit var appContext: Context

fun isAppContextInitialized(): Boolean = ::appContext.isInitialized

actual fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    return Room.databaseBuilder<AppDatabase>(
        context = appContext,
        name = "habr_rss.db"
    )
}
