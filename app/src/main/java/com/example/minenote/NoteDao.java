package com.example.minenote;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface NoteDao {

    @Insert
    long insert(Note note);

    // 全部笔记
    @Query("SELECT * FROM Note ORDER BY id DESC")
    List<Note> getAllNotes();

    // 根据 ID 获取笔记
    @Query("SELECT * FROM Note WHERE id = :noteId LIMIT 1")
    Note getNoteById(int noteId);

    // 修改笔记
    @Update
    void update(Note note);

    // 删除笔记
    @Delete
    void delete(Note note);

    // 搜索笔记
    @Query("SELECT * FROM Note WHERE title LIKE '%' || :keyword || '%' ORDER BY id DESC")
    List<Note> searchNotes(String keyword);

    // 获取某个文件夹里的笔记
    @Query("SELECT * FROM Note WHERE folderId = :folderId ORDER BY id DESC")
    List<Note> getNotesByFolder(int folderId);

    // 获取未分类笔记
    @Query("SELECT * FROM Note WHERE folderId IS NULL ORDER BY id DESC")
    List<Note> getUncategorizedNotes();

    // 把笔记移动到文件夹
    @Query("UPDATE Note SET folderId = :folderId WHERE id = :noteId")
    void moveNoteToFolder(int noteId, Integer folderId);
}