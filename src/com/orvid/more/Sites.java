package com.orvid.more;

import java.util.Arrays;
import java.util.List;

public final class Sites {

    public static final Site ODYSEE = new Odysee();
    public static final Site BITCHUTE = new BitChute();
    public static final Site RUTUBE = new Rutube();
    public static final Site APARAT = new Aparat();
    public static final Site CCC = new Ccc();
    public static final Site COMMONS = new Commons();
    public static final Site NASA = new Nasa();
    public static final Site MEDIATHEK = new Mediathek();
    public static final Site NINEGAG = new NineGag();
    public static final Site COUB = new Coub();
    public static final Site STEAM = new Steam();
    public static final Site TED = new Ted();
    public static final Site CHZZK = new Chzzk();
    public static final Site SOOP = new Soop();
    public static final Site IMGUR = new Imgur();
    public static final Site MASTODON = new Mastodon();
    public static final Site MIXKIT = new Mixkit();
    public static final Site BELLESA = new Bellesa();

    public static final List<Site> ALL = Arrays.asList(ODYSEE, BITCHUTE, RUTUBE, APARAT, CCC, COMMONS, NASA,
            MEDIATHEK, NINEGAG, COUB, STEAM, TED, CHZZK, SOOP, IMGUR, MASTODON, MIXKIT, BELLESA);

    private Sites() {
    }

    public static Site forUrl(String url) {
        if (url == null) return null;
        for (Site s : ALL) if (s.handles(url)) return s;
        return null;
    }
}
