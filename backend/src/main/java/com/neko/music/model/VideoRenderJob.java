package com.neko.music.model;

import java.sql.Timestamp;

public class VideoRenderJob {
    private String id;
    private int userId;
    private int musicId;
    private double startSec;
    private double durationSec;
    private boolean watermarked;
    private String status;
    private String errorMessage;
    private String outputRelPath;
    private Timestamp createdAt;
    private Timestamp finishedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public int getMusicId() {
        return musicId;
    }

    public void setMusicId(int musicId) {
        this.musicId = musicId;
    }

    public double getStartSec() {
        return startSec;
    }

    public void setStartSec(double startSec) {
        this.startSec = startSec;
    }

    public double getDurationSec() {
        return durationSec;
    }

    public void setDurationSec(double durationSec) {
        this.durationSec = durationSec;
    }

    public boolean isWatermarked() {
        return watermarked;
    }

    public void setWatermarked(boolean watermarked) {
        this.watermarked = watermarked;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getOutputRelPath() {
        return outputRelPath;
    }

    public void setOutputRelPath(String outputRelPath) {
        this.outputRelPath = outputRelPath;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Timestamp finishedAt) {
        this.finishedAt = finishedAt;
    }
}
