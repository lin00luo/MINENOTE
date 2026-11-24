package com.example.minenote;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface ReaderDao {
    @Insert long insertSource(ReaderSource source);
    @Update void updateSource(ReaderSource source);
    @Query("SELECT * FROM reader_sources ORDER BY addedAt DESC") List<ReaderSource> getSources();
    @Query("SELECT * FROM reader_sources WHERE id=:id LIMIT 1") ReaderSource getSource(int id);
    @Query("DELETE FROM reader_sources WHERE id=:id") void deleteSource(int id);
    @Insert long insertArticle(ReaderArticle article);
    @Update void updateArticle(ReaderArticle article);
    @Query("SELECT * FROM reader_articles ORDER BY publishedAt DESC, id DESC") List<ReaderArticle> getArticles();
    @Query("SELECT * FROM reader_articles WHERE isSaved=1 ORDER BY publishedAt DESC") List<ReaderArticle> getSavedArticles();
    @Query("SELECT * FROM reader_articles WHERE sourceId=:sourceId ORDER BY publishedAt DESC") List<ReaderArticle> getArticlesForSource(int sourceId);
    @Query("SELECT * FROM reader_articles WHERE id=:id LIMIT 1") ReaderArticle getArticle(int id);
    @Query("SELECT * FROM reader_articles WHERE url=:url LIMIT 1") ReaderArticle findByUrl(String url);
    @Query("SELECT * FROM reader_articles WHERE sourceId=:sourceId AND guid=:guid LIMIT 1") ReaderArticle findByGuid(int sourceId, String guid);
    @Query("UPDATE reader_articles SET isRead=:read WHERE id=:id") void setRead(int id, boolean read);
    @Query("UPDATE reader_articles SET isSaved=:saved WHERE id=:id") void setSaved(int id, boolean saved);
    @Query("DELETE FROM reader_articles WHERE sourceId=:sourceId") void deleteArticlesForSource(int sourceId);
}