package com.example.minenote;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface FolderDao {

    @Insert
    long insert(Folder folder);

    @Query("SELECT * FROM Folder ORDER BY sortOrder ASC, id ASC")
    List<Folder> getAllFolders();

    @Query("UPDATE Folder SET name = :name WHERE id = :folderId")
    void renameFolder(
            int folderId,
            String name
    );

    @Delete
    void delete(Folder folder);

    // 删除文件夹前，把里面的笔记变成未分类
    @Query("UPDATE Note SET folderId = NULL WHERE folderId = :folderId")
    void clearFolderFromNotes(int folderId);

    // 保存文件夹的新顺序
    @Query("UPDATE Folder SET sortOrder = :sortOrder WHERE id = :folderId")
    void updateFolderOrder(
            int folderId,
            int sortOrder
    );
}
