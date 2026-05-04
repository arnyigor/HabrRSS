package com.arny.habrrss.data.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE feed_items ADD COLUMN publishedAtEpoch INTEGER")
        connection.execSQL("ALTER TABLE feed_items ADD COLUMN cachedArticleJson TEXT")
    }
}
