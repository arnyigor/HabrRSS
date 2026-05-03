package com.arny.habrrss.data.database

import androidx.room.Room
import androidx.room.RoomDatabase
import com.arny.habrrss.data.database.AppDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import java.io.File

// Desktop uses Room with bundled SQLite for full offline support
actual fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val dbFolder = File(System.getProperty("user.home"), ".habrrss")
    if (!dbFolder.exists()) {
        dbFolder.mkdirs()
    }
    val dbPath = File(dbFolder, "habr_rss.db").absolutePath

    return Room.databaseBuilder<AppDatabase>(name = dbPath)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
}
