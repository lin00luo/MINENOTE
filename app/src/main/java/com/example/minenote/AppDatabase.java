package com.example.minenote;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(
        entities = {
                Note.class,
                Folder.class,
                ReaderSource.class,
                ReaderArticle.class
        },
        version = 6,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    public abstract NoteDao noteDao();

    public abstract FolderDao folderDao();

    public abstract ReaderDao readerDao();
}
