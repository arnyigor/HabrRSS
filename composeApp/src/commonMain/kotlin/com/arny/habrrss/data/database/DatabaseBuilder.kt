package com.arny.habrrss.data.database

import androidx.room.RoomDatabase

/**
 * Platform-specific database builder.
 * Android: Returns Room database builder
 * JVM: Throws UnsupportedOperationException (uses InMemoryFeedDao)
 */
expect fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase>