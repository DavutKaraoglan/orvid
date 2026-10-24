import com.orvid.net.*;
import com.orvid.tor.Tor;
import com.orvid.play.*;
import com.orvid.search.Source;
import java.io.*;
import java.net.HttpURLConnection;
import java.util.*;

public class MediaWarmProbe {
    static final int BYTES = 256 * 1024;

    static long[] fetch(String c, String url, String ref, String cookie, int bytes) throws Exception {
        long t = System.currentTimeMillis();
        long[] out = {-1, -1};
        Fetch.on(c, () -> {
            HttpURLConnection h = NightProbe.follow(url, ref, cookie, "bytes=0-" + (bytes - 1));
            int code = h.getResponseCode();
            if (code >= 400) throw new IOException("HTTP " + code);
            out[0] = System.currentTimeMillis() - t;
            byte[] buf = new byte[65536];
            int total = 0, r;
            try (InputStream in = h.getInputStream()) {
                while (total < bytes && (r = in.read(buf)) > 0) total += r;
                if (bytes <= 1) while (in.read(buf) > 0) { }
            }
            out[1] = System.currentTimeMillis() - t;
            return null;
        });
        return out;
    }

    public static void main(String[] a) throws Exception {
        Fetch.setTorMode(true);
        File dir = new File(System.getenv().getOrDefault("TORDIR", System.getenv("PREFIX") + "/tmp/orvid-tor4")); dir.mkdirs();
        Tor.get().start(dir, System.getenv("PREFIX") + "/tmp/torx/jni/arm64-v8a/libtor.so", (int) ProcessHandle.current().pid());
        System.out.println("tor " + Tor.get().awaitReady(180000));
        Thread.sleep(3000);
        com.orvid.ext.Registry.reload(new File(System.getenv("ROOT")));
        PrintWriter out = new PrintWriter(new FileWriter(a[0], true), true);
        out.println("#key\tmode\tresolve_ms\tfirst_byte_ms\tbytes_ms\tkind");
        int videos = Integer.parseInt(System.getenv().getOrDefault("VIDEOS", "4"));
        for (int i = 1; i < a.length; i++) {
            Source s = null;
            for (Source x : com.orvid.ui.Sources.current()) if (x.key.equals(a[i])) s = x;
            if (s == null) continue;
            final Source fs = s;
            List<org.schabi.newpipe.extractor.stream.StreamInfoItem> items;
            try {
                items = Fetch.retrying(Fetch.newCircuit("w" + s.key), cc -> fs.search(cc, NightProbe.query(fs), new com.orvid.search.Filters(), null).items);
            } catch (Exception e) { out.println(s.key + "\tsearch\t-\t-\t-\t" + NightProbe.clean(e)); continue; }
            int n = 0;
            for (var it : items) {
                if (n >= videos) break;
                boolean warm = n % 2 == 1;
                try {
                    long t0 = System.currentTimeMillis();
                    Resolve.Stream r = Resolve.preview(it.getUrl());
                    long resolveMs = System.currentTimeMillis() - t0;
                    Direct d = r.direct;
                    boolean hls = d.hls || NightProbe.playlist(r.circuit, d.url, d.referer, d.cookie);
                    if (hls) { out.println(s.key + "\tskip\t" + resolveMs + "\t-\t-\tHLS"); n++; continue; }
                    if (warm) fetch(r.circuit, d.url, d.referer, d.cookie, 1);
                    Thread.sleep(1500);
                    long[] b = fetch(r.circuit, d.url, d.referer, d.cookie, BYTES);
                    out.println(s.key + "\t" + (warm ? "warm" : "cold") + "\t" + resolveMs + "\t" + b[0] + "\t" + b[1] + "\tMP4 " + java.net.URI.create(d.url).getHost());
                } catch (Exception e) {
                    out.println(s.key + "\t" + (warm ? "warm" : "cold") + "\t-\t-\t-\t" + NightProbe.clean(e));
                }
                n++;
            }
        }
        out.println("#done " + new Date());
        Tor.get().stop();
        Thread.sleep(300);
        System.exit(0);
    }
}
