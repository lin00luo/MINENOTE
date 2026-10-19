package com.example.minenote;

import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

public class AppDatabaseMigrationV4 {

    public static final Migration MIGRATION_3_4 =
            new Migration(3, 4) {

                @Override
                public void migrate(
                        SupportSQLiteDatabase database
                ) {

                    database.execSQL(
                            "ALTER TABLE Note " +
                                    "ADD COLUMN folderId INTEGER"
                    );
                }
            };
}