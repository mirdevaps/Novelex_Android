package com.novelex.dramas;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Drama implements Serializable {
    private int year;
    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }
    private String id;
    private String title;
    private String poster;
    private String banner;
    private String synopsis;
    private String genre;
    private String audio;
    private int episodesCount;
    private List<Episode> episodes = new ArrayList<>();

    public Drama() {}

    public Drama(String id, String title, String poster, String banner, String synopsis, String genre, String audio, int episodesCount) {
        this.id = id;
        this.title = title;
        this.poster = poster;
        this.banner = banner;
        this.synopsis = synopsis;
        this.genre = genre;
        this.audio = audio;
        this.episodesCount = episodesCount;
    }

    public String getId() { return id != null ? id : ""; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title != null ? title : "Drama Sem Título"; }
    public void setTitle(String title) { this.title = title; }

    public String getPoster() { return poster != null ? poster : ""; }
    public void setPoster(String poster) { this.poster = poster; }

    public String getBanner() { return (banner != null && !banner.isEmpty()) ? banner : poster; }
    public void setBanner(String banner) { this.banner = banner; }

    public String getSynopsis() { return (synopsis != null && !synopsis.isEmpty()) ? synopsis : "Sinopse em breve..."; }
    public void setSynopsis(String synopsis) { this.synopsis = synopsis; }

    public String getGenre() { return (genre != null && !genre.isEmpty()) ? genre : "Drama"; }
    public void setGenre(String genre) { this.genre = genre; }

    public String getAudio() { return (audio != null && !audio.isEmpty()) ? audio : "Dublado"; }
    public void setAudio(String audio) { this.audio = audio; }

    public int getEpisodesCount() { return episodesCount > 0 ? episodesCount : episodes.size(); }
    public void setEpisodesCount(int episodesCount) { this.episodesCount = episodesCount; }

    public List<Episode> getEpisodes() { return episodes; }
    public void setEpisodes(List<Episode> episodes) { this.episodes = episodes; }
}