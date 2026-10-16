import com.orvid.net.*;
import com.orvid.tor.Tor;
import java.io.File;
import java.net.HttpURLConnection;
public class FixProbe {
    static int head(String c, String url, String ref) throws Exception {
        return Fetch.on(c, () -> { HttpURLConnection h = Fetch.open(url); h.setRequestProperty("User-Agent", Fetch.USER_AGENT); h.setRequestProperty("Range", "bytes=0-1023"); if (ref != null) h.setRequestProperty("Referer", ref); int code = h.getResponseCode(); return code; });
    }
    public static void main(String[] a) throws Exception {
        Fetch.setTorMode(true);
        File dir = new File(System.getenv("PREFIX") + "/tmp/orvid-tor"); dir.mkdirs();
        Tor.get().start(dir, System.getenv("PREFIX") + "/tmp/torx/jni/arm64-v8a/libtor.so", (int) ProcessHandle.current().pid());
        System.out.println("tor " + Tor.get().awaitReady(180000));
        String c = Fetch.newCircuit("fix");
        try {
            var p = com.orvid.adult.Tubes.PORNICOM.search(c, "milf", null);
            for (int i = 0; i < 3; i++) System.out.println("pornicom item " + p.items.get(i).getUrl());
            try { var m = com.orvid.adult.Tubes.PORNICOM.resolve(c, p.items.get(0).getUrl()); System.out.println("pornicom ok " + m.mp4); } catch (Exception e) { System.out.println("pornicom resolve FAIL " + e); }
        } catch (Exception e) { System.out.println("pornicom search FAIL " + e); }
        try {
            var p = com.orvid.more.Sites.ODYSEE.search(c, "cat", null);
            for (int i = 0; i < 4; i++) {
                String u = p.items.get(i).getUrl();
                try { var d = com.orvid.more.Sites.ODYSEE.resolve(c, u); System.out.println("odysee " + u.substring(19, Math.min(u.length(), 80)) + " -> " + head(c, d.url, null) + " " + d.url); } catch (Exception e) { System.out.println("odysee FAIL " + u + " " + e); }
            }
        } catch (Exception e) { System.out.println("odysee search FAIL " + e); }
        try {
            var p = com.orvid.ep.Eporner.search(c, "milf", null);
            for (int i = 0; i < 2; i++) {
                String u = p.items.get(i).getUrl();
                try { var m = com.orvid.ep.Eporner.resolve(c, u); System.out.println("eporner " + head(c, m.mp4, null) + " " + m.mp4.substring(0, Math.min(90, m.mp4.length()))); } catch (Exception e) { System.out.println("eporner FAIL " + u + " " + e); }
            }
        } catch (Exception e) { System.out.println("eporner search FAIL " + e); }
        try {
            var p = com.orvid.vk.Vk.search(c, "cat", null);
            String u = p.items.get(0).getUrl();
            long t = System.currentTimeMillis();
            try { var v = com.orvid.vk.Vk.fetch(c, com.orvid.vk.Vk.videoId(u)); System.out.println("vk ok " + (System.currentTimeMillis() - t) + "ms files=" + v.files.keySet()); } catch (Exception e) { System.out.println("vk FAIL after " + (System.currentTimeMillis() - t) + "ms " + u + " " + e); }
        } catch (Exception e) { System.out.println("vk search FAIL " + e); }
        Tor.get().stop(); Thread.sleep(300); System.exit(0);
    }
}
