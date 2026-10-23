package com.orvid.yt;
import com.orvid.net.*;
import com.orvid.tor.Tor;
import java.io.File;
public class ChannelProbe {
    public static void main(String[] a) throws Exception {
        Fetch.setTorMode(true);
        File dir = new File(System.getenv("PREFIX") + "/tmp/orvid-tor"); dir.mkdirs();
        Tor.get().start(dir, System.getenv("PREFIX") + "/tmp/torx/jni/arm64-v8a/libtor.so", (int) ProcessHandle.current().pid());
        Tor.get().awaitReady(180000);
        for (String id : new String[]{"UC51-ljNFrvY5ZmV0C_61XcA", "UCuAXFkgsw1L7xaCfnd5JJOw", "UCX6OQ3DkcsbYNE6H8uQQuVA"}) {
            String c = Fetch.newCircuit("cf" + id);
            Visionos.ensureVisitor(c);
            long t = System.currentTimeMillis();
            Channel.Page p = Fetch.retrying(c, cc -> Channel.first(cc, id));
            long ms = System.currentTimeMillis() - t;
            var i = p.items.isEmpty() ? null : p.items.get(0);
            System.out.println(id + ": " + ms + "ms name=" + p.name + " subs=" + p.subscribers + " avatar=" + (p.avatar != null ? p.avatar.substring(0, 40) + "..." + p.avatar.substring(p.avatar.length() - 12) : null) + " videos=" + p.items.size() + " next=" + (p.continuation != null)
                + (i == null ? "" : " | first: " + i.getName() + " / " + i.getUploaderName() + " / dur " + i.getDuration() + " / views " + i.getViewCount() + " / " + i.getTextualUploadDate() + " / thumbs " + i.getThumbnails().size() + " / ch " + i.getUploaderUrl()));
            if (p.continuation != null) {
                t = System.currentTimeMillis();
                Channel.Page m = Fetch.retrying(c, cc -> Channel.more(cc, p.continuation, p.name, id));
                System.out.println("   more: " + (System.currentTimeMillis() - t) + "ms videos=" + m.items.size() + " next=" + (m.continuation != null) + " owner=" + (m.items.isEmpty() ? null : m.items.get(0).getUploaderName()));
            }
            {
                com.grack.nanojson.JsonObject b = new com.grack.nanojson.JsonObject(); b.put("browseId", id); b.put("params", "EgZ2aWRlb3PyBgQKAjoA");
                com.grack.nanojson.JsonObject b2 = new com.grack.nanojson.JsonObject(); b2.putAll(b);
                java.lang.reflect.Field f = Channel.class.getDeclaredField("FIRST_MASK"); f.setAccessible(true);
                String full = Related.post(c, "https://www.youtube.com/youtubei/v1/browse?prettyPrint=false", b, "full", null);
                String masked = Related.post(c, "https://www.youtube.com/youtubei/v1/browse?prettyPrint=false", b2, "masked", (String) f.get(null));
                System.out.println("   size full " + full.length() / 1024 + "KB, masked " + masked.length() / 1024 + "KB");
            }
        }
        Tor.get().stop(); Thread.sleep(300); System.exit(0);
    }
}
