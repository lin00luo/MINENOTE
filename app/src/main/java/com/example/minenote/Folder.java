package com.example.minenote;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity
public class Folder {

    @PrimaryKey(autoGenerate = true)
    private int id;

    private String name;

    private int sortOrder;

    public Folder(String name) {
        this.name = name;
        this.sortOrder = 0;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
