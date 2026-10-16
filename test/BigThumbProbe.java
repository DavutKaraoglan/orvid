import com.orvid.net.*;
import com.orvid.tor.Tor;
import com.orvid.search.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.util.*;
public class BigThumbProbe {
    static int size(String u, String ref) {
        try {
            return Fetch.on(Fetch.newCircuit("t"), () -> {
                HttpURLConnection h = Fetch.open(u);
                h.setRequestProperty("User-Agent", Fetch.USER_AGENT);
                if (ref != null) h.setRequestProperty("Referer", ref);
                if (h.getResponseCode() >= 400) return -h.getResponseCode();
                try (InputStream in = h.getInputStream()) { return in.readAllBytes().length; }
            });
        } catch (Exception e) { return -1; }
    }
    public static void main(String[] a) throws Exception {
        Fetch.setTorMode(true);
        Source.adultUnlocked = true;
        File dir = new File(System.getenv("PREFIX") + "/tmp/orvid-tor"); dir.mkdirs();
        Tor.get().start(dir, System.getenv("PREFIX") + "/tmp/torx/jni/arm64-v8a/libtor.so", (int) ProcessHandle.current().pid());
        System.out.println("tor " + Tor.get().awaitReady(180000));
        Filters f = new Filters();
        for (Source s : com.orvid.ui.Sources.EXTRA) f.set(s, true);
        Set<String> want = new HashSet<>(Arrays.asList(a));
        for (Source s : com.orvid.ui.Sources.EXTRA) {
            if (!want.contains(s.key)) continue;
            try {
                var items = Hedge.call(Fetch.newCircuit("s"), () -> Fetch.newCircuit("s"), 2500, c -> s.search(c, BenchProbe.query(s), f, null)).value.items;
                var it = items.get(0);
                String ref = it.getUrl().replaceAll("^(https?://[^/]+/).*", "$1");
                System.out.println("== " + s.key + " " + it.getThumbnails().size() + " thumbnails, page " + it.getUrl());
                for (var t : it.getThumbnails()) System.out.println("   " + t.getWidth() + "x" + t.getHeight() + " " + size(t.getUrl(), ref) / 1024 + "KB " + t.getUrl());
                String pick = com.orvid.ui.Ui.thumbnail(it.getThumbnails(), 360);
                System.out.println("   app picks (360px cell): " + pick);
            } catch (Exception e) { System.out.println("== " + s.key + " FAIL " + e); }
        }
        Tor.get().stop(); Thread.sleep(300); System.exit(0);
    }
}
