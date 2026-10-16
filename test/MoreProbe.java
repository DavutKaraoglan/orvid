import com.orvid.net.*;
import com.orvid.tor.Tor;
import com.orvid.more.*;
import java.io.File;
import java.util.concurrent.*;
public class MoreProbe {
    public static void main(String[] a) throws Exception {
        Fetch.setTorMode(true);
        File dir = new File(System.getenv("PREFIX") + "/tmp/orvid-tor"); dir.mkdirs();
        Tor.get().start(dir, System.getenv("PREFIX") + "/tmp/torx/jni/arm64-v8a/libtor.so", (int) ProcessHandle.current().pid());
        System.out.println("tor " + Tor.get().awaitReady(180000));
        String[][] queries = {{"Odysee","cats"},{"BitChute","cats"},{"Rutube","cats"},{"Aparat","cats"},{"media.ccc.de","tor"},
            {"Wikimedia Commons","cat"},{"NASA","mars"},{"ARD/ZDF Mediathek","katzen"},{"9GAG","cats"},{"Coub","cats"},{"Steam","portal"},{"TED","climate"},{"Chzzk","cat"},{"SOOP","cat"},{"Imgur","cats"},{"Mastodon","cats"},{"Mixkit","cat"},{"Bellesa","massage"}};
        ExecutorService pool = Executors.newFixedThreadPool(20);
        java.util.List<Future<String>> out = new java.util.ArrayList<>();
        for (String[] q : queries) {
            if (a.length > 0 && !java.util.Arrays.asList(a).contains(q[0])) continue;
            Site site = null;
            for (Site s : Sites.ALL) if (s.name.equals(q[0])) site = s;
            final Site s = site;
            out.add(pool.submit(() -> {
                String c = Fetch.newCircuit(s.name);
                long t0 = System.currentTimeMillis();
                StringBuilder r = new StringBuilder(s.name + ": ");
                try {
                    var p = Fetch.retrying(c, cc -> s.search(cc, q[1], null));
                    long t1 = System.currentTimeMillis();
                    var it = p.items.get(0);
                    r.append(p.items.size()).append(" items ").append(t1 - t0).append("ms next=").append(p.next != null)
                     .append(" | ").append(it.getName()).append(" | ").append(it.getUploaderName()).append(" | dur=").append(it.getDuration())
                     .append(" views=").append(it.getViewCount()).append(" date=").append(it.getUploadDate() != null)
                     .append(" thumb=").append(!it.getThumbnails().isEmpty()).append(" handles=").append(Sites.forUrl(it.getUrl()) == s);
                    var d = Fetch.retrying(c, cc -> s.resolve(cc, it.getUrl()));
                    r.append("\n   -> ").append(d.hls ? "HLS " : "MP4 ").append(d.title).append(" / ").append(d.author)
                     .append(" ").append(d.url.length() > 110 ? d.url.substring(0, 110) : d.url).append(" ").append(System.currentTimeMillis() - t1).append("ms");
                    if (p.next != null) {
                        var p2 = Fetch.retrying(c, cc -> s.search(cc, q[1], p.next));
                        r.append("\n   page2 ").append(p2.items.size()).append(" first=").append(p2.items.isEmpty() ? "-" : p2.items.get(0).getName());
                    }
                } catch (Exception e) { r.append("FAIL ").append(e); }
                return r.toString();
            }));
        }
        for (Future<String> f : out) System.out.println(f.get());
        Tor.get().stop(); Thread.sleep(500); System.exit(0);
    }
}
