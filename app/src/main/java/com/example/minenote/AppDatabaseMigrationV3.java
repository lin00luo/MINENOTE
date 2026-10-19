package com.example.minenote;

import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

public class AppDatabaseMigrationV3 {

    public static final Migration MIGRATION_2_3 =
            new Migration(2, 3) {

                @Override
                public void migrate(
                        SupportSQLiteDatabase database
                ) {

                    database.execSQL(
                            "CREATE TABLE IF NOT EXISTS Folder (" +
                                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                    "name TEXT" +
                                    ")"
                    );
                }
            };
}