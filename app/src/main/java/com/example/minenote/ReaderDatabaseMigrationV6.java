package com.example.minenote;

import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

public final class ReaderDatabaseMigrationV6 {
    public static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS reader_sources (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT, url TEXT, feedUrl TEXT, addedAt INTEGER NOT NULL, lastFetchedAt INTEGER NOT NULL, lastError TEXT)");
            db.execSQL("CREATE TABLE IF NOT EXISTS reader_articles (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sourceId INTEGER NOT NULL, guid TEXT, title TEXT, url TEXT, author TEXT, summary TEXT, content TEXT, publishedAt INTEGER NOT NULL, isRead INTEGER NOT NULL, isSaved INTEGER NOT NULL)");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_reader_articles_sourceId_guid ON reader_articles(sourceId, guid)");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_reader_articles_url ON reader_articles(url)");
        }
    };
}