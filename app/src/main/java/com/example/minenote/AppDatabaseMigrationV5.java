package com.example.minenote;

import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

public class AppDatabaseMigrationV5 {

    public static final Migration MIGRATION_4_5 =
            new Migration(4, 5) {
                @Override
                public void migrate(
                        @NonNull SupportSQLiteDatabase database
                ) {
                    database.execSQL(
                            "ALTER TABLE Folder " +
                                    "ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0"
                    );
                }
            };
}
