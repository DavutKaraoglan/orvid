import com.orvid.net.*;
import com.orvid.tor.Tor;
import com.orvid.adult.*;
import java.io.File;
import java.net.HttpURLConnection;
public class TubeNewProbe {
    public static void main(String[] a) throws Exception {
        Fetch.setTorMode(true);
        File dir = new File(System.getenv("PREFIX") + "/tmp/orvid-tor"); dir.mkdirs();
        Tor.get().start(dir, System.getenv("PREFIX") + "/tmp/torx/jni/arm64-v8a/libtor.so", (int) ProcessHandle.current().pid());
        System.out.println("tor " + Tor.get().awaitReady(180000));
        HtmlTube[] ts = a.length > 0 ? new HtmlTube[]{Tubes.FREEPORN8, Tubes.PORNWHITE} : new HtmlTube[]{Tubes.FREEPORN8, Tubes.PORNWHITE, Tubes.SHOOSHTIME, Tubes.THREEMOVS, Tubes.PERFECTGIRLS};
        for (HtmlTube t : ts) {
            String c = Fetch.newCircuit("t" + t.serviceId);
            StringBuilder r = new StringBuilder(t.serviceId + ": ");
            try {
                var p = Fetch.retrying(c, cc -> t.search(cc, "milf", null));
                var it = p.items.get(0);
                r.append(p.items.size()).append(" items next=").append(p.next).append(" | ").append(it.getName()).append(" | dur=")
                 .append(it.getDuration()).append(" views=").append(it.getViewCount()).append(" thumb=")
                 .append(it.getThumbnails().isEmpty() ? "-" : it.getThumbnails().get(0).getUrl().replaceAll("^https://([^/]+)/.*", "$1"));
                if (p.next != null) {
                    var p2 = Fetch.retrying(c, cc -> t.search(cc, "milf", p.next));
                    r.append("\n   page2 ").append(p2.items.size()).append(" first=").append(p2.items.isEmpty() ? "-" : p2.items.get(0).getName());
                }
                var m = Fetch.retrying(c, cc -> t.resolve(cc, it.getUrl()));
                r.append("\n   -> ").append(m.title).append(" ").append(m.mp4.length() > 100 ? m.mp4.substring(0, 100) : m.mp4);
                int code = Fetch.on(c, () -> {
                    HttpURLConnection h = Fetch.open(m.mp4);
                    h.setRequestProperty("User-Agent", Fetch.USER_AGENT);
                    h.setRequestProperty("Range", "bytes=0-65535");
                    h.setRequestProperty("Referer", m.referer);
                    int cd = h.getResponseCode();
                    if (cd < 400) h.getInputStream().readAllBytes();
                    return cd;
                });
                r.append("\n   media HTTP ").append(code);
            } catch (Exception e) { r.append("FAIL ").append(e); }
            System.out.println(r);
        }
        Tor.get().stop(); Thread.sleep(500); System.exit(0);
    }
}
