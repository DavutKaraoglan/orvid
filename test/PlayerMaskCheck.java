package com.orvid.yt;
import com.grack.nanojson.*;
import com.orvid.net.*;
import com.orvid.tor.Tor;
import java.io.File;
import java.lang.reflect.*;
import java.util.*;
public class PlayerMaskCheck {
    static String sig(JsonObject r) {
        StringBuilder sb = new StringBuilder();
        sb.append("status=").append(r.getObject("playabilityStatus").getString("status", null));
        JsonObject d = r.getObject("videoDetails");
        for (String k : new String[]{"title", "author", "channelId", "lengthSeconds"}) sb.append(" ").append(k).append("=").append(d.getString(k, null));
        sb.append(" desc=").append(d.getString("shortDescription", "").length()).append(" live=").append(d.getBoolean("isLiveContent", false));
        sb.append(" sb=").append(r.getObject("storyboards").getObject("playerStoryboardSpecRenderer").getString("spec", "").split("\\|").length);
        JsonObject s = r.getObject("streamingData");
        sb.append(" hls=").append(s.getString("hlsManifestUrl", null) != null);
        List<String> f = new ArrayList<>();
        for (String k : new String[]{"formats", "adaptiveFormats"}) { JsonArray a = s.getArray(k); for (int i = 0; i < a.size(); i++) { JsonObject o = a.getObject(i); f.add(o.getInt("itag") + "/" + o.getString("mimeType", "").split(";")[0] + "/" + o.getString("contentLength", "") + "/" + (o.getString("url", null) != null) + "/" + (o.getObject("initRange").getString("end", null) != null) + "/" + o.getObject("audioTrack").getString("id", "")); } }
        Collections.sort(f);
        sb.append(" formats=").append(f.size()).append(" ").append(f.hashCode());
        JsonArray t = r.getObject("captions").getObject("playerCaptionsTracklistRenderer").getArray("captionTracks");
        List<String> c = new ArrayList<>(); for (int i = 0; i < t.size(); i++) c.add(t.getObject(i).getString("languageCode", "") + t.getObject(i).getString("kind", "") + (t.getObject(i).getString("baseUrl", null) != null));
        Collections.sort(c); sb.append(" captions=").append(c);
        return sb.toString();
    }
    public static void main(String[] a) throws Exception {
        Fetch.setTorMode(true);
        File dir = new File(System.getenv("PREFIX") + "/tmp/orvid-tor"); dir.mkdirs();
        Tor.get().start(dir, System.getenv("PREFIX") + "/tmp/torx/jni/arm64-v8a/libtor.so", (int) ProcessHandle.current().pid());
        System.out.println("tor ready " + Tor.get().awaitReady(240000) + " " + Tor.get().state());
        Method req = Visionos.class.getDeclaredMethod("request", String.class, String.class, String.class, String.class, String.class); req.setAccessible(true);
        Field mf = Visionos.class.getDeclaredField("PLAYER_MASK"); mf.setAccessible(true); String mask = (String) mf.get(null);
        Field uf = Visionos.class.getDeclaredField("PLAYER_URL"); uf.setAccessible(true); String url = (String) uf.get(null);
        long fullMs = 0, maskMs = 0, fullKb = 0, maskKb = 0; int same = 0, n = 0;
        for (String id : new String[]{"dQw4w9WgXcQ", "jNQXAC9IVRw", "kJQP7kiw5Fk", "9bZkp7q19f0", "FtutLA63Cp8"}) {
            String c = Fetch.newCircuit("pm" + id);
            Method vm = Visionos.class.getDeclaredMethod("visitor", String.class); vm.setAccessible(true); String visitor = (String) vm.invoke(null, c);
            JsonObject ctxClient = new JsonObject(); ctxClient.put("clientName", "VISIONOS"); 
            Field cn = Visionos.class.getDeclaredField("CLIENT_NAME"); cn.setAccessible(true);
            Field cv = Visionos.class.getDeclaredField("CLIENT_VERSION"); cv.setAccessible(true);
            JsonObject client = new JsonObject();
            client.put("clientName", cn.get(null)); client.put("clientVersion", cv.get(null)); client.put("deviceMake", "Apple"); client.put("deviceModel", "RealityDevice17,1"); client.put("osName", "visionOS"); client.put("osVersion", "26.5.23O471"); client.put("hl", "en"); client.put("gl", "US"); client.put("visitorData", visitor);
            JsonObject context = new JsonObject(); context.put("client", client);
            JsonObject body = new JsonObject(); body.put("context", context); body.put("videoId", id); body.put("contentCheckOk", true); body.put("racyCheckOk", true);
            String json = JsonWriter.string(body);
            long t = System.currentTimeMillis(); String full = (String) req.invoke(null, c, url, json, visitor, null); long t1 = System.currentTimeMillis();
            String m = (String) req.invoke(null, c, url, json, visitor, mask); long t2 = System.currentTimeMillis();
            String sf = sig(JsonParser.object().from(full)), sm = sig(JsonParser.object().from(m));
            boolean eq = sf.equals(sm); if (eq) same++; n++;
            fullMs += t1 - t; maskMs += t2 - t1; fullKb += full.length() / 1024; maskKb += m.length() / 1024;
            System.out.println(id + ": text " + full.length() / 1024 + "KB -> " + m.length() / 1024 + "KB, " + (t1 - t) + "ms / " + (t2 - t1) + "ms, same=" + eq);
            if (!eq) { System.out.println("  full:   " + sf); System.out.println("  masked: " + sm); }
            else System.out.println("  " + sm.substring(0, Math.min(200, sm.length())));
        }
        System.out.println("same " + same + "/" + n + ", text " + fullKb + "KB -> " + maskKb + "KB");
        Tor.get().stop(); Thread.sleep(300); System.exit(0);
    }
}
