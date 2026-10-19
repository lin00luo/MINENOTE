package com.example.minenote;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity
public class Note {

    @PrimaryKey(autoGenerate = true)
    private int id;

    private String title;
    private String content;
    private long createdAt;

    // null 表示“未分类”
    private Integer folderId;

    // Room 使用这个构造方法
    public Note(
            String title,
            String content,
            long createdAt,
            Integer folderId
    ) {
        this.title = title;
        this.content = content;
        this.createdAt = createdAt;
        this.folderId = folderId;
    }

    // 原来的代码继续可以使用
    @Ignore
    public Note(String title, String content) {
        this.title = title;
        this.content = content;
        this.createdAt = System.currentTimeMillis();
        this.folderId = null;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public Integer getFolderId() {
        return folderId;
    }

    public void setFolderId(Integer folderId) {
        this.folderId = folderId;
    }
}