import com.orvid.net.*;
import com.orvid.tor.Tor;
import java.io.File;
import java.nio.file.*;
public class XhProbe {
    public static void main(String[] a) throws Exception {
        if (a.length > 0) {
            for (String hex : Files.readAllLines(Paths.get(a[0]))) System.out.println(com.orvid.xh.XHamster.class.getDeclaredMethod("decipher", String.class) != null ? callDecipher(hex) : "");
            return;
        }
        Fetch.setTorMode(true);
        File dir = new File(System.getenv("PREFIX") + "/tmp/orvid-tor"); dir.mkdirs();
        Tor.get().start(dir, System.getenv("PREFIX") + "/tmp/torx/jni/arm64-v8a/libtor.so", (int) ProcessHandle.current().pid());
        System.out.println("tor " + Tor.get().awaitReady(180000));
        String c = Fetch.newCircuit("x");
        long t0 = System.currentTimeMillis();
        com.orvid.xh.XHamster.Page p = Fetch.retrying(c, cc -> com.orvid.xh.XHamster.search(cc, "test", null));
        System.out.println("search: " + p.items.size() + " next=" + p.next + " dur=" + p.items.get(0).getDuration() + " views=" + p.items.get(0).getViewCount() + " " + (System.currentTimeMillis() - t0) + "ms");
        t0 = System.currentTimeMillis();
        com.orvid.xh.XHamster.Media m = Fetch.retrying(c, cc -> com.orvid.xh.XHamster.resolve(cc, p.items.get(0).getUrl()));
        System.out.println("resolve: hls=" + (m.hls != null) + " title=" + !m.title.isEmpty() + " " + (System.currentTimeMillis() - t0) + "ms");
        for (String ref : new String[] {null, m.referer}) {
            int code = Fetch.on(c, () -> {
                java.net.HttpURLConnection h = Fetch.open(m.hls);
                h.setRequestProperty("User-Agent", Fetch.USER_AGENT);
                if (ref != null) h.setRequestProperty("Referer", ref);
                return h.getResponseCode();
            });
            System.out.println("playlist " + (ref == null ? "plain" : "with referer") + ": " + code);
        }
        Tor.get().stop(); Thread.sleep(1000); System.exit(0);
    }
    static String callDecipher(String hex) throws Exception {
        java.lang.reflect.Method d = com.orvid.xh.XHamster.class.getDeclaredMethod("decipher", String.class);
        d.setAccessible(true);
        return (String) d.invoke(null, hex);
    }
}
