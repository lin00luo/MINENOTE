package com.example.minenote;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "reader_articles", indices = {@Index(value={"sourceId","guid"}, unique=true), @Index(value={"url"}, unique=true)})
public class ReaderArticle {
    @PrimaryKey(autoGenerate = true) public int id;
    public int sourceId;
    public String guid;
    public String title;
    public String url;
    public String author;
    public String summary;
    public String content;
    public long publishedAt;
    public boolean isRead;
    public boolean isSaved;
    public ReaderArticle(int sourceId, String guid, String title, String url, String author,
                         String summary, String content, long publishedAt) {
        this.sourceId=sourceId; this.guid=guid; this.title=title; this.url=url;
        this.author=author; this.summary=summary; this.content=content;
        this.publishedAt=publishedAt; this.isRead=false; this.isSaved=false;
    }
}