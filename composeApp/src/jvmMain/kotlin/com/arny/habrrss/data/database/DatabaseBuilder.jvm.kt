package com.arny.habrrss.data.database

import androidx.room.RoomDatabase

// Desktop fallback - Room не поддерживается, используем in-memory
actual fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    throw UnsupportedOperationException("Desktop uses InMemoryFeedDao")
}