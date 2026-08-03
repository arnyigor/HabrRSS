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

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS article_local_state (
                articleId TEXT NOT NULL PRIMARY KEY,
                isRead INTEGER NOT NULL DEFAULT 0,
                lastOpenedAt INTEGER,
                scrollPosition INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS favorite_articles (
                articleId TEXT NOT NULL PRIMARY KEY,
                createdAt INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_favorite_articles_createdAt ON favorite_articles(createdAt)")
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS favorite_tags (
                tagId TEXT NOT NULL PRIMARY KEY,
                title TEXT,
                createdAt INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS favorite_hubs (
                hubId TEXT NOT NULL PRIMARY KEY,
                title TEXT,
                createdAt INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )

        connection.execSQL(
            """
            INSERT OR REPLACE INTO article_local_state(articleId, isRead, lastOpenedAt, scrollPosition)
            SELECT id, isRead, CASE WHEN isRead = 1 THEN fetchedAt ELSE NULL END, 0
            FROM feed_items
            WHERE isRead = 1
            """.trimIndent()
        )
        connection.execSQL(
            """
            INSERT OR REPLACE INTO favorite_articles(articleId, createdAt)
            SELECT id, fetchedAt
            FROM feed_items
            WHERE isBookmarked = 1
            """.trimIndent()
        )

        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS feed_items_new (
                id TEXT NOT NULL PRIMARY KEY,
                feedId TEXT NOT NULL,
                title TEXT NOT NULL,
                summary TEXT NOT NULL,
                descriptionHtml TEXT,
                url TEXT NOT NULL,
                imageUrl TEXT,
                authorName TEXT,
                authorProfileUrl TEXT,
                publishedAt TEXT,
                publishedAtEpoch INTEGER,
                tagsJson TEXT NOT NULL,
                hubsJson TEXT NOT NULL,
                rating TEXT,
                commentsCount INTEGER,
                cachedArticleJson TEXT,
                fetchedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        connection.execSQL(
            """
            INSERT INTO feed_items_new(
                id, feedId, title, summary, descriptionHtml, url, imageUrl, authorName,
                authorProfileUrl, publishedAt, publishedAtEpoch, tagsJson, hubsJson, rating,
                commentsCount, cachedArticleJson, fetchedAt
            )
            SELECT
                id, feedId, title, summary, descriptionHtml, url, imageUrl, authorName,
                authorProfileUrl, publishedAt, publishedAtEpoch, tagsJson, hubsJson, rating,
                commentsCount, cachedArticleJson, fetchedAt
            FROM feed_items
            """.trimIndent()
        )
        connection.execSQL("DROP TABLE feed_items")
        connection.execSQL("ALTER TABLE feed_items_new RENAME TO feed_items")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_feed_items_feedId ON feed_items(feedId)")
    }
}


val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sync_state (
                sourceKey TEXT NOT NULL PRIMARY KEY,
                mode TEXT NOT NULL,
                status TEXT NOT NULL,
                nextPage INTEGER NOT NULL,
                pagesCountSnapshot INTEGER,
                pagesProcessed INTEGER NOT NULL,
                receivedCount INTEGER NOT NULL,
                uniqueCount INTEGER NOT NULL,
                failedPage INTEGER,
                errorCode TEXT,
                startedAtEpochMillis INTEGER NOT NULL,
                updatedAtEpochMillis INTEGER NOT NULL,
                completedAtEpochMillis INTEGER
            )
            """.trimIndent()
        )
    }
}


val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS index_feed_items_feedId_publishedAtEpoch_fetchedAt ON feed_items(feedId, publishedAtEpoch, fetchedAt)"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS index_feed_items_publishedAtEpoch ON feed_items(publishedAtEpoch)"
        )
    }
}
