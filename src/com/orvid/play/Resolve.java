package com.orvid.play;

import com.orvid.net.Fetch;
import com.orvid.yt.Visionos;

public final class Resolve {

    public static final class Stream {
        public final Direct direct;
        public final String circuit;

        Stream(Direct direct, String circuit) {
            this.direct = direct;
            this.circuit = circuit;
        }
    }

    private Resolve() {
    }

    public static Stream preview(String url) throws Exception {
        String id = Visionos.videoId(url);
        if (id != null) {
            Visionos.Prepared p = Visionos.prepareNow(id).get();
            Visionos.Format best = null;
            for (Visionos.Format f : p.video.formats) {
                if (!f.isVideo() || f.url == null || !f.mime.startsWith("video/mp4") || f.height <= 0) continue;
                if (f.height <= 360 && (best == null || f.height > best.height || best.height > 360)) best = f;
                else if (best == null) best = f;
            }
            if (best == null) return null;
            return new Stream(new Direct(p.video.title, p.video.author, best.url, false, null), p.circuit);
        }
        String c = Visionos.takeCircuit();
        Direct d = Fetch.retrying(c, cc -> direct(cc, url));
        return d == null ? null : new Stream(d, d.circuit != null ? d.circuit : c);
    }

    private static final long PREPARED_TTL_MS = 20 * 60 * 1000;
    private static final java.util.Map<String, Object[]> PREPARED = new java.util.LinkedHashMap<String, Object[]>(32, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<String, Object[]> eldest) {
            return size() > 24;
        }
    };
    private static final java.util.concurrent.ExecutorService PREPARE = java.util.concurrent.Executors.newFixedThreadPool(2);

    public static boolean preparable(String url) {
        if (url == null || com.orvid.adult.Tubes.webOnly(url)) return false;
        com.orvid.ext.ExtSource ext = com.orvid.ext.Registry.forUrl(url);
        if (ext != null) return !ext.webPlayback;
        if (com.orvid.dm.Dailymotion.videoId(url) != null || com.orvid.vk.Vk.videoId(url) != null) return true;
        return com.orvid.tw.Twitch.vodId(url) != null || com.orvid.tw.Twitch.clipSlug(url) != null
                || com.orvid.pt.PeerTube.handles(url) || com.orvid.ia.Archive.itemId(url) != null
                || com.orvid.xv.Xvideos.handles(url) || com.orvid.ay.Aylo.handles(url)
                || com.orvid.ep.Eporner.handles(url) || com.orvid.xh.XHamster.handles(url)
                || com.orvid.tn.Tnaflix.handles(url) || com.orvid.adult.Txxx.handles(url)
                || com.orvid.adult.RedGifs.handles(url) || com.orvid.adult.PornOne.handles(url)
                || com.orvid.more.Sites.forUrl(url) != null || com.orvid.adult.Tubes.forUrl(url) != null;
    }

    @SuppressWarnings("unchecked")
    public static java.util.concurrent.Future<Stream> prepare(String url) {
        if (!preparable(url)) return null;
        synchronized (PREPARED) {
            Object[] known = PREPARED.get(url);
            if (known != null && System.currentTimeMillis() - (long) known[1] < PREPARED_TTL_MS) {
                return (java.util.concurrent.Future<Stream>) known[0];
            }
            com.orvid.net.Queued<Stream> f = new com.orvid.net.Queued<>(() -> {
                com.orvid.net.Hedge.Result<Direct> r = com.orvid.net.Hedge.call(Fetch.newCircuit("prep"),
                        () -> Fetch.newCircuit("prep"), Fetch.torMode() ? 2500 : 0, c -> direct(c, url));
                if (r.value == null) return null;
                return new Stream(r.value, r.value.circuit != null ? r.value.circuit : r.circuit);
            });
            PREPARE.execute(f);
            PREPARED.put(url, new Object[]{f, System.currentTimeMillis()});
            return f;
        }
    }

    @SuppressWarnings("unchecked")
    public static java.util.concurrent.Future<Stream> takePrepared(String url) {
        synchronized (PREPARED) {
            Object[] known = PREPARED.remove(url);
            if (known == null || System.currentTimeMillis() - (long) known[1] >= PREPARED_TTL_MS) return null;
            java.util.concurrent.Future<Stream> f = (java.util.concurrent.Future<Stream>) known[0];
            if (f instanceof com.orvid.net.Queued && ((com.orvid.net.Queued<?>) f).waiting()) {
                f.cancel(false);
                return null;
            }
            return f;
        }
    }

    private static Direct direct(String c, String url) throws Exception {
        com.orvid.ext.ExtSource ext = com.orvid.ext.Registry.forUrl(url);
        if (ext != null) return ext.webPlayback ? null : ext.resolve(c, url);
        String dm = com.orvid.dm.Dailymotion.videoId(url);
        if (dm != null) {
            com.orvid.dm.Dailymotion.Video v = com.orvid.dm.Dailymotion.resolve(c, dm);
            return v.hls == null ? null : new Direct(v.title, v.author, v.hls, true, null).via(c);
        }
        String vk = com.orvid.vk.Vk.videoId(url);
        if (vk != null) {
            com.orvid.vk.Vk.Video v = com.orvid.vk.Vk.fetch(c, vk);
            String hls = v.files.containsKey("hls") ? v.files.get("hls") : v.files.get("hls_ondemand");
            if (hls != null) return new Direct(v.title, v.author, hls, true, null).via(c);
            int cap = Fetch.torMode() ? 720 : 1080;
            for (int h : new int[] {1080, 720, 480, 360, 240}) {
                if (h <= cap && v.files.containsKey("mp4_" + h)) return new Direct(v.title, v.author, v.files.get("mp4_" + h), false, null).via(c);
            }
            return null;
        }
        if (com.orvid.tw.Twitch.vodId(url) != null) {
            com.orvid.tw.Twitch.Media m = com.orvid.tw.Twitch.vod(c, com.orvid.tw.Twitch.vodId(url));
            return new Direct(m.title, m.author, m.hls, true, null);
        }
        if (com.orvid.tw.Twitch.clipSlug(url) != null) {
            com.orvid.tw.Twitch.Media m = com.orvid.tw.Twitch.clip(c, com.orvid.tw.Twitch.clipSlug(url));
            return new Direct(m.title, m.author, m.mp4, false, null);
        }
        if (com.orvid.pt.PeerTube.handles(url)) {
            com.orvid.pt.PeerTube.Media m = com.orvid.pt.PeerTube.resolve(c, url);
            return m.mp4 != null ? new Direct(m.title, m.author, m.mp4, false, null) : new Direct(m.title, m.author, m.hls, true, null);
        }
        if (com.orvid.ia.Archive.itemId(url) != null) {
            com.orvid.ia.Archive.Media m = com.orvid.ia.Archive.resolve(c, com.orvid.ia.Archive.itemId(url));
            return new Direct(m.title, m.author, m.mp4, false, null);
        }
        if (com.orvid.xv.Xvideos.handles(url)) {
            com.orvid.xv.Xvideos.Media m = com.orvid.xv.Xvideos.resolve(c, url);
            return (m.mp4 != null ? new Direct(m.title, m.author, m.mp4, false, null) : new Direct(m.title, m.author, m.hls, true, null)).sprite(m.sprite);
        }
        if (com.orvid.ay.Aylo.handles(url)) {
            com.orvid.ay.Aylo.Media m = com.orvid.ay.Aylo.resolve(c, url);
            return new Direct(m.title, "", m.hls, true, com.orvid.ay.Aylo.REFERER);
        }
        if (com.orvid.ep.Eporner.handles(url)) {
            com.orvid.ep.Eporner.Media m = com.orvid.ep.Eporner.resolve(c, url);
            return new Direct(m.title, "", m.mp4, false, null);
        }
        if (com.orvid.xh.XHamster.handles(url)) {
            com.orvid.xh.XHamster.Media m = com.orvid.xh.XHamster.resolve(c, url);
            return new Direct(m.title, m.author, m.hls, true, m.referer).sprite(m.sprite);
        }
        if (com.orvid.tn.Tnaflix.handles(url)) {
            com.orvid.tn.Tnaflix.Media m = com.orvid.tn.Tnaflix.resolve(c, url);
            return new Direct(m.title, "", m.mp4, false, null);
        }
        if (com.orvid.adult.Txxx.handles(url)) {
            com.orvid.adult.Txxx.Media m = com.orvid.adult.Txxx.resolve(c, url);
            return new Direct(m.title, m.author, m.mp4, false, m.referer);
        }
        if (com.orvid.adult.RedGifs.handles(url)) {
            com.orvid.adult.RedGifs.Media m = com.orvid.adult.RedGifs.resolve(c, url);
            return new Direct(m.title, m.author, m.mp4, false, null);
        }
        if (com.orvid.adult.PornOne.handles(url)) {
            com.orvid.adult.PornOne.Media m = com.orvid.adult.PornOne.resolve(c, url);
            return new Direct(m.title, "", m.mp4, false, null);
        }
        com.orvid.more.Site site = com.orvid.more.Sites.forUrl(url);
        if (site != null) return site.resolve(c, url);
        com.orvid.adult.HtmlTube tube = com.orvid.adult.Tubes.forUrl(url);
        if (tube != null) {
            com.orvid.adult.HtmlTube.Media m = tube.resolve(c, url);
            return new Direct(m.title, "", m.mp4, false, m.referer);
        }
        return null;
    }
}
