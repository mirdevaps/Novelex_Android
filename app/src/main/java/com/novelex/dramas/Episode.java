package com.novelex.dramas;

import java.io.Serializable;

public class Episode implements Serializable {
    private String thumbnail;
    public String getThumbnail() { return thumbnail == null ? "" : thumbnail; }
    public void setThumbnail(String thumbnail) { this.thumbnail = thumbnail; }
    private int number;
    private String title;
    private String streamUrl;

    public Episode() {}

    public Episode(int number, String title, String streamUrl) {
        this.number = number;
        this.title = title;
        this.streamUrl = streamUrl;
    }

    public int getNumber() { return number; }
    public void setNumber(int number) { this.number = number; }

    public String getTitle() { return (title != null && !title.isEmpty()) ? title : ("Capítulo " + number); }
    public void setTitle(String title) { this.title = title; }

    public String getStreamUrl() { return streamUrl != null ? streamUrl : ""; }
    public void setStreamUrl(String streamUrl) { this.streamUrl = streamUrl; }
}