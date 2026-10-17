package com.orvid.play;

import androidx.media3.exoplayer.ExoPlayer;

import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.ArrayList;
import java.util.List;

public final class Session {

    private static ExoPlayer player;
    private static String url;

    public static String title = "";
    public static String author = "";
    public static CharSequence description = "";
    public static String sub = "";
    public static String thumb;
    public static String avatar;
    public static String circuit;
    public static String relatedToken;
    public static final List<StreamInfoItem> related = new ArrayList<>();
    public static volatile List<StreamInfoItem> lastSearch = new ArrayList<>();

    private Session() {
    }

    public static boolean active() {
        return player != null;
    }

    public static boolean has(String pageUrl) {
        return player != null && url != null && url.equals(pageUrl);
    }

    public static ExoPlayer player() {
        return player;
    }

    public static String url() {
        return url;
    }

    public static void begin(ExoPlayer p, String pageUrl, String thumbUrl) {
        release();
        player = p;
        url = pageUrl;
        thumb = thumbUrl;
        title = "";
        author = "";
        description = "";
        sub = "";
        circuit = null;
        relatedToken = null;
        related.clear();
    }

    public static void release() {
        if (player != null) player.release();
        player = null;
        url = null;
        related.clear();
    }
}
