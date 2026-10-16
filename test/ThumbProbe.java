import com.orvid.net.*;
import com.orvid.tor.Tor;
import java.io.File;
import java.net.HttpURLConnection;
import java.util.*;
import java.util.concurrent.*;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
public class ThumbProbe {
    static int get(String c, String url, String ref) {
        try {
            return Fetch.on(c, () -> {
                HttpURLConnection h = Fetch.open(url);
                h.setConnectTimeout(12000); h.setReadTimeout(15000);
                h.setRequestProperty("User-Agent", Fetch.USER_AGENT);
                h.setRequestProperty("Accept", "image/webp,image/jpeg,image/png,image/*;q=0.8");
                if (ref != null) h.setRequestProperty("Referer", ref);
                int code = h.getResponseCode();
                if (code < 400) h.getInputStream().readAllBytes();
                return code;
            });
        } catch (Exception e) { return -1; }
    }
    interface Search { List<StreamInfoItem> run(String c) throws Exception; }
    public static void main(String[] a) throws Exception {
        Fetch.setTorMode(true);
        File dir = new File(System.getenv("PREFIX") + "/tmp/orvid-tor"); dir.mkdirs();
        Tor.get().start(dir, System.getenv("PREFIX") + "/tmp/torx/jni/arm64-v8a/libtor.so", (int) ProcessHandle.current().pid());
        System.out.println("tor " + Tor.get().awaitReady(180000));
        Map<String, Search> all = new LinkedHashMap<>();
        for (com.orvid.adult.HtmlTube t : com.orvid.adult.Tubes.ALL) if (!t.web) all.put("tube" + t.serviceId, c -> t.search(c, "milf", null).items);
        all.put("xvideos", c -> com.orvid.xv.Xvideos.search(c, "milf", null).items);
        all.put("pornhub", c -> com.orvid.ay.Aylo.search(c, "milf", null).items);
        all.put("eporner", c -> com.orvid.ep.Eporner.search(c, "milf", null).items);
        all.put("xhamster", c -> com.orvid.xh.XHamster.search(c, "milf", null).items);
        all.put("tnaflix", c -> com.orvid.tn.Tnaflix.search(c, "milf", null).items);
        all.put("txxx", c -> com.orvid.adult.Txxx.search(c, "milf", null).items);
        all.put("redgifs", c -> com.orvid.adult.RedGifs.search(c, "milf", null).items);
        all.put("pornone", c -> com.orvid.adult.PornOne.search(c, "milf", null).items);
        all.put("twitch", c -> com.orvid.tw.Twitch.search(c, "minecraft", null).items);
        all.put("archive", c -> com.orvid.ia.Archive.search(c, "apollo", null).items);
        for (com.orvid.more.Site s : com.orvid.more.Sites.ALL) all.put(s.name, c -> s.search(c, s.name.equals("NASA") ? "mars" : "cat", null).items);
        ExecutorService pool = Executors.newFixedThreadPool(6);
        List<Future<String>> out = new ArrayList<>();
        for (Map.Entry<String, Search> e : all.entrySet()) out.add(pool.submit(() -> {
            String c = Fetch.newCircuit(e.getKey());
            try {
                List<StreamInfoItem> items = Fetch.retrying(c, cc -> e.getValue().run(cc));
                int n = 0, plain = 0, ref = 0;
                for (StreamInfoItem it : items) {
                    if (it.getThumbnails().isEmpty()) continue;
                    if (++n > 4) break;
                    String u = it.getThumbnails().get(0).getUrl();
                    String page = it.getUrl().replaceAll("^(https?://[^/]+/).*", "$1");
                    if (get(c, u, null) < 400) plain++;
                    if (get(c + "r", u, page) < 400) ref++;
                }
                return e.getKey() + ": thumbs=" + Math.min(n, 4) + " plain_ok=" + plain + " referer_ok=" + ref;
            } catch (Exception ex) { return e.getKey() + ": search FAIL " + ex.getMessage(); }
        }));
        for (Future<String> f : out) System.out.println(f.get());
        Tor.get().stop(); Thread.sleep(500); System.exit(0);
    }
}
