package com.example.minenote;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "reader_sources")
public class ReaderSource {
    @PrimaryKey(autoGenerate = true) public int id;
    public String title;
    public String url;
    public String feedUrl;
    public long addedAt;
    public long lastFetchedAt;
    public String lastError;
    public ReaderSource(String title, String url, String feedUrl, long addedAt) {
        this.title=title; this.url=url; this.feedUrl=feedUrl; this.addedAt=addedAt;
        this.lastFetchedAt=0; this.lastError="";
    }
}