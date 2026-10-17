package com.orvid.ui;

import com.orvid.R;
import com.orvid.adult.HtmlTube;
import com.orvid.adult.PornOne;
import com.orvid.adult.Tubes;
import com.orvid.adult.RedGifs;
import com.orvid.adult.Txxx;
import com.orvid.ay.Aylo;
import com.orvid.ep.Eporner;
import com.orvid.ia.Archive;
import com.orvid.more.Site;
import com.orvid.more.Sites;
import com.orvid.pt.PeerTube;
import com.orvid.search.Filters;
import com.orvid.search.Source;
import com.orvid.tn.Tnaflix;
import com.orvid.tw.Twitch;
import com.orvid.xh.XHamster;
import com.orvid.web.WebSites;
import com.orvid.xv.Xvideos;

import java.util.Arrays;
import java.util.List;

public final class Sources {

    private Sources() {
    }

    public static List<Source> current() {
        List<Source> out = new java.util.ArrayList<>();
        for (Source s : BUILTIN) if (com.orvid.ext.Registry.engineOn(s.key)) out.add(s);
        out.addAll(com.orvid.ext.Registry.sources());
        java.util.Collections.sort(out, (a, b) -> Boolean.compare(a.adult, b.adult));
        return out;
    }

    private static final List<Source> BUILTIN = Arrays.asList(
            new Source("twitch", "Twitch", R.drawable.ic_src_twitch, Twitch.SERVICE_ID, 0.8, true, false) {
                @Override
                public Page search(String circuit, String query, Filters f, String next) throws Exception {
                    Twitch.Page p = Twitch.search(circuit, query, next);
                    return new Page(p.items, p.next);
                }
            },
            new Source("peertube", "PeerTube", R.drawable.ic_src_peertube, PeerTube.SERVICE_ID, 0.7, true, false) {
                @Override
                public Page search(String circuit, String query, Filters f, String next) throws Exception {
                    PeerTube.Page p = PeerTube.search(circuit, query, next, f.hideAdult);
                    return new Page(p.items, p.next);
                }
            },
            new Source("archive", "Internet Archive", R.drawable.ic_src_archive, Archive.SERVICE_ID, 0.7, true, false) {
                @Override
                public Page search(String circuit, String query, Filters f, String next) throws Exception {
                    Archive.Page p = Archive.search(circuit, query, next);
                    return new Page(p.items, p.next);
                }
            },
            site("odysee", R.drawable.ic_src_odysee, Sites.ODYSEE, 0.6),
            site("rutube", R.drawable.ic_src_rutube, Sites.RUTUBE, 0.6).script(com.orvid.search.Query.CYRILLIC),
            site("bitchute", R.drawable.ic_src_bitchute, Sites.BITCHUTE, 0.5),
            site("aparat", R.drawable.ic_src_aparat, Sites.APARAT, 0.5).script(com.orvid.search.Query.ARABIC),
            site("mediathek", R.drawable.ic_src_mediathek, Sites.MEDIATHEK, 0.6),
            site("ccc", R.drawable.ic_src_ccc, Sites.CCC, 0.6),
            site("commons", R.drawable.ic_src_commons, Sites.COMMONS, 0.5),
            site("nasa", R.drawable.ic_src_nasa, Sites.NASA, 0.5),
            site("ninegag", R.drawable.ic_src_ninegag, Sites.NINEGAG, 0.5),
            site("coub", R.drawable.ic_src_coub, Sites.COUB, 0.5),
            site("steam", R.drawable.ic_src_steam, Sites.STEAM, 0.5),
            site("ted", R.drawable.ic_src_ted, Sites.TED, 0.6),
            site("imgur", R.drawable.ic_src_imgur, Sites.IMGUR, 0.5),
            site("mastodon", R.drawable.ic_src_mastodon, Sites.MASTODON, 0.4),
            site("mixkit", R.drawable.ic_src_mixkit, Sites.MIXKIT, 0.4),
            site("chzzk", R.drawable.ic_src_chzzk, Sites.CHZZK, 0.5).script(com.orvid.search.Query.HANGUL),
            site("europeana", R.drawable.ic_src_europeana, Sites.EUROPEANA, 0.5),
            site("ok", R.drawable.ic_src_ok, Sites.OK, 0.5).script(com.orvid.search.Query.CYRILLIC),
            site("soop", R.drawable.ic_src_soop, Sites.SOOP, 0.5).script(com.orvid.search.Query.HANGUL),
            new Source("xvideos", "XVIDEOS (18+)", R.drawable.ic_src_xvideos, Xvideos.SERVICE_ID, 0.6, false, true) {
                @Override
                public Page search(String circuit, String query, Filters f, String next) throws Exception {
                    Xvideos.Page p = Xvideos.search(circuit, query, next);
                    return new Page(p.items, p.next);
                }
            },
            new Source("aylo", "Pornhub (18+)", R.drawable.ic_src_pornhub, Aylo.SERVICE_ID, 0.6, false, true) {
                @Override
                public Page search(String circuit, String query, Filters f, String next) throws Exception {
                    Aylo.Page p = Aylo.search(circuit, query, next);
                    return new Page(p.items, p.next);
                }
            },
            new Source("eporner", "Eporner (18+)", R.drawable.ic_src_eporner, Eporner.SERVICE_ID, 0.6, false, true) {
                @Override
                public Page search(String circuit, String query, Filters f, String next) throws Exception {
                    Eporner.Page p = Eporner.search(circuit, query, next);
                    return new Page(p.items, p.next);
                }
            },
            new Source("xhamster", "xHamster (18+)", R.drawable.ic_src_xhamster, XHamster.SERVICE_ID, 0.6, false, true) {
                @Override
                public Page search(String circuit, String query, Filters f, String next) throws Exception {
                    XHamster.Page p = XHamster.search(circuit, query, next);
                    return new Page(p.items, p.next);
                }
            },
            new Source("tnaflix", "TNAFlix (18+)", R.drawable.ic_src_tnaflix, Tnaflix.SERVICE_ID, 0.6, false, true) {
                @Override
                public Page search(String circuit, String query, Filters f, String next) throws Exception {
                    Tnaflix.Page p = Tnaflix.search(circuit, query, next);
                    return new Page(p.items, p.next);
                }
            },
            new Source("txxx", "Txxx (18+)", R.drawable.ic_src_txxx, Txxx.SERVICE_ID, 0.6, false, true) {
                @Override
                public Page search(String circuit, String query, Filters f, String next) throws Exception {
                    Txxx.Page p = Txxx.search(circuit, query, next);
                    return new Page(p.items, p.next);
                }
            },
            new Source("redgifs", "RedGIFs (18+)", R.drawable.ic_src_redgifs, RedGifs.SERVICE_ID, 0.5, false, true) {
                @Override
                public Page search(String circuit, String query, Filters f, String next) throws Exception {
                    RedGifs.Page p = RedGifs.search(circuit, query, next);
                    return new Page(p.items, p.next);
                }
            },
            new Source("pornone", "PornOne (18+)", R.drawable.ic_src_pornone, PornOne.SERVICE_ID, 0.6, false, true) {
                @Override
                public Page search(String circuit, String query, Filters f, String next) throws Exception {
                    PornOne.Page p = PornOne.search(circuit, query, next);
                    return new Page(p.items, p.next);
                }
            },
            tube("hellporno", "HellPorno (18+)", R.drawable.ic_src_hellporno, Tubes.HELLPORNO),
            tube("pornhat", "Pornhat (18+)", R.drawable.ic_src_pornhat, Tubes.PORNHAT),
            tube("bravotube", "BravoTube (18+)", R.drawable.ic_src_bravotube, Tubes.BRAVOTUBE),
            tube("hqporner", "HQporner (18+)", R.drawable.ic_src_hqporner, Tubes.HQPORNER),
            tube("erome", "Erome (18+)", R.drawable.ic_src_erome, Tubes.EROME),
            tube("porngo", "PornGo (18+)", R.drawable.ic_src_porngo, Tubes.PORNGO),
            tube("gayporntube", "GayPornTube (18+)", R.drawable.ic_src_gayporntube, Tubes.GAYPORNTUBE),
            tube("rule34video", "Rule34Video (18+)", R.drawable.ic_src_rule34video, Tubes.RULE34VIDEO),
            tube("xxxymovies", "XXXYMovies (18+)", R.drawable.ic_src_xxxymovies, Tubes.XXXYMOVIES),
            tube("zbporn", "ZBPorn (18+)", R.drawable.ic_src_zbporn, Tubes.ZBPORN),
            tube("drtuber", "DrTuber (18+)", R.drawable.ic_src_drtuber, Tubes.DRTUBER),
            tube("porndoe", "PornDoe (18+)", R.drawable.ic_src_porndoe, Tubes.PORNDOE),
            tube("thisvid", "ThisVid (18+)", R.drawable.ic_src_thisvid, Tubes.THISVID),
            tube("freeporn8", "FreePorn8 (18+)", R.drawable.ic_src_freeporn8, Tubes.FREEPORN8),
            tube("pornwhite", "PornWhite (18+)", R.drawable.ic_src_pornwhite, Tubes.PORNWHITE),
            tube("shooshtime", "ShooshTime (18+)", R.drawable.ic_src_shooshtime, Tubes.SHOOSHTIME),
            tube("threemovs", "3Movs (18+)", R.drawable.ic_src_threemovs, Tubes.THREEMOVS),
            tube("perfectgirls", "PerfectGirls (18+)", R.drawable.ic_src_perfectgirls, Tubes.PERFECTGIRLS),
            tube("youjizz", "YouJizz (18+)", R.drawable.ic_src_youjizz, Tubes.YOUJIZZ),
            tube("playvids", "PlayVids (18+)", R.drawable.ic_src_playvids, Tubes.PLAYVIDS),
            tube("xfights", "XFights (18+)", R.drawable.ic_src_xfights, Tubes.XFIGHTS),
            tube("anysex", "AnySex (18+)", R.drawable.ic_src_anysex, Tubes.ANYSEX),
            tube("dafreeporn", "DaFreePorn (18+)", R.drawable.ic_src_dafreeporn, Tubes.DAFREEPORN),
            tube("juicyvid", "JuicyVid (18+)", R.drawable.ic_src_juicyvid, Tubes.JUICYVID),
            tube("megatube", "MegaTube (18+)", R.drawable.ic_src_megatube, Tubes.MEGATUBE),
            tube("sortporn", "SortPorn (18+)", R.drawable.ic_src_sortporn, Tubes.SORTPORN),
            tube("fapcat", "FapCat (18+)", R.drawable.ic_src_fapcat, Tubes.FAPCAT),
            tube("maturekinkys", "MatureKinkys (18+)", R.drawable.ic_src_maturekinkys, Tubes.MATUREKINKYS),
            tube("xozilla", "Xozilla (18+)", R.drawable.ic_src_xozilla, Tubes.XOZILLA),
            tube("femdomvc", "FemdomVC (18+)", R.drawable.ic_src_femdomvc, Tubes.FEMDOMVC),
            adultSite("bellesa", "Bellesa (18+)", R.drawable.ic_src_bellesa, Sites.BELLESA),
            web("spankbang", "SpankBang (18+)", R.drawable.ic_src_spankbang, WebSites.SPANKBANG),
            web("fourkporn", "4kporn (18+)", R.drawable.ic_src_fourkporn, WebSites.FOURKPORN),
            web("jable", "Jable (18+)", R.drawable.ic_src_jable, WebSites.JABLE),
            web("missav", "MissAV (18+)", R.drawable.ic_src_missav, WebSites.MISSAV),
            web("ashemaletube", "aShemaleTube (18+)", R.drawable.ic_src_ashemaletube, WebSites.ASHEMALETUBE),
            web("boyfriendtv", "BoyfriendTV (18+)", R.drawable.ic_src_boyfriendtv, WebSites.BOYFRIENDTV));

    private static Source site(String key, int icon, Site site, double weight) {
        return new Source(key, site.name, icon, site.serviceId, weight, true, false) {
            @Override
            public Page search(String circuit, String query, Filters f, String next) throws Exception {
                return site.search(circuit, query, next);
            }
        };
    }

    private static Source adultSite(String key, String name, int icon, Site site) {
        return new Source(key, name, icon, site.serviceId, 0.6, false, true) {
            @Override
            public boolean usesWebView() {
                return true;
            }

            @Override
            public Page search(String circuit, String query, Filters f, String next) throws Exception {
                return site.search(circuit, query, next);
            }
        };
    }

    private static Source web(String key, String name, int icon, WebSites.Site site) {
        return new Source(key, name, icon, site.serviceId, 0.6, false, true) {
            @Override
            public Page search(String circuit, String query, Filters f, String next) throws Exception {
                int page = next == null ? 1 : Integer.parseInt(next);
                java.util.List<org.schabi.newpipe.extractor.stream.StreamInfoItem> items =
                        WebSites.search(com.orvid.app.OrvidApp.get(), site, query, page);
                return new Page(items, !items.isEmpty() && site.pages() ? Integer.toString(page + 1) : null);
            }
        };
    }

    private static Source tube(String key, String name, int icon, HtmlTube tube) {
        return new Source(key, name, icon, tube.serviceId, 0.6, false, true) {
            @Override
            public Page search(String circuit, String query, Filters f, String next) throws Exception {
                HtmlTube.Page p = tube.search(circuit, query, next);
                return new Page(p.items, p.next);
            }
        };
    }

    static Source forUrl(String url) {
        if (url == null) return null;
        Source ext = com.orvid.ext.Registry.forUrl(url);
        if (ext != null) return ext;
        if (com.orvid.tw.Twitch.vodId(url) != null || com.orvid.tw.Twitch.clipSlug(url) != null) return byService(Twitch.SERVICE_ID);
        if (PeerTube.handles(url)) return byService(PeerTube.SERVICE_ID);
        if (Archive.itemId(url) != null) return byService(Archive.SERVICE_ID);
        if (Xvideos.handles(url)) return byService(Xvideos.SERVICE_ID);
        if (Aylo.handles(url)) return byService(Aylo.SERVICE_ID);
        if (Eporner.handles(url)) return byService(Eporner.SERVICE_ID);
        if (XHamster.handles(url)) return byService(XHamster.SERVICE_ID);
        if (Tnaflix.handles(url)) return byService(Tnaflix.SERVICE_ID);
        if (Txxx.handles(url)) return byService(Txxx.SERVICE_ID);
        if (RedGifs.handles(url)) return byService(RedGifs.SERVICE_ID);
        if (PornOne.handles(url)) return byService(PornOne.SERVICE_ID);
        Site site = Sites.forUrl(url);
        if (site != null) return byService(site.serviceId);
        HtmlTube tube = Tubes.forUrl(url);
        return tube != null ? byService(tube.serviceId) : null;
    }

    static Source byService(int serviceId) {
        for (Source s : current()) if (s.serviceId == serviceId) return s;
        return null;
    }
}
