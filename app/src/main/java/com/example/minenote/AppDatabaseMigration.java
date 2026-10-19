package com.example.minenote;

import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

public class AppDatabaseMigration {

    public static final Migration MIGRATION_1_2 =
            new Migration(1, 2) {

                @Override
                public void migrate(
                        SupportSQLiteDatabase database
                ) {

                    database.execSQL(
                            "ALTER TABLE Note " +
                                    "ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0"
                    );

                    database.execSQL(
                            "UPDATE Note " +
                                    "SET createdAt = " +
                                    "(strftime('%s','now') * 1000) " +
                                    "WHERE createdAt = 0"
                    );
                }
            };
}