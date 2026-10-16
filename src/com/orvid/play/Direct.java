package com.orvid.play;

public final class Direct {
    public final String title;
    public final String author;
    public final String url;
    public final boolean hls;
    public final String referer;
    public String circuit;

    public Direct(String title, String author, String url, boolean hls, String referer) {
        this.title = title == null ? "" : title;
        this.author = author == null ? "" : author;
        this.url = url;
        this.hls = hls;
        this.referer = referer;
    }

    public Direct via(String circuit) {
        this.circuit = circuit;
        return this;
    }
}
