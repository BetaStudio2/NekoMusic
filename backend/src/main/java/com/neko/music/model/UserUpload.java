package com.neko.music.model;

import java.time.LocalDateTime;

public class UserUpload {
    private int id;
    private int userId;
    private String title;
    private String artist;
    private String album;
    private String language;
    private String tags;
    private int duration;
    private String musicFilePath;
    private String coverFilePath;
    private String lyricsFilePath;
    private String status; // pending, approved, rejected
    private LocalDateTime createdAt;
    
    public UserUpload() {
    }
    
    public UserUpload(int userId, String title, String artist, String language, String tags, String album, int duration) {
        this.userId = userId;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.language = language;
        this.tags = tags;
        this.duration = duration;
        this.status = "pending";
        this.createdAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    public int getId() {
        return id;
    }
    
    public void setId(int id) {
        this.id = id;
    }
    
    public int getUserId() {
        return userId;
    }
    
    public void setUserId(int userId) {
        this.userId = userId;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public String getArtist() {
        return artist;
    }
    
    public void setArtist(String artist) {
        this.artist = artist;
    }
    
    public String getAlbum() {
        return album;
    }
    
    public void setAlbum(String album) {
        this.album = album;
    }
    
    public String getLanguage() {
        return language;
    }
    
    public void setLanguage(String language) {
        this.language = language;
    }
    
    public String getTags() {
        return tags;
    }
    
    public void setTags(String tags) {
        this.tags = tags;
    }
    
    public int getDuration() {
        return duration;
    }
    
    public void setDuration(int duration) {
        this.duration = duration;
    }
    
    public String getMusicFilePath() {
        return musicFilePath;
    }
    
    public void setMusicFilePath(String musicFilePath) {
        this.musicFilePath = musicFilePath;
    }
    
    public String getCoverFilePath() {
        return coverFilePath;
    }
    
    public void setCoverFilePath(String coverFilePath) {
        this.coverFilePath = coverFilePath;
    }
    
    public String getLyricsFilePath() {
        return lyricsFilePath;
    }
    
    public void setLyricsFilePath(String lyricsFilePath) {
        this.lyricsFilePath = lyricsFilePath;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}