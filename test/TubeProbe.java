import com.orvid.net.*;
import com.orvid.tor.Tor;
import com.orvid.adult.*;
import java.io.File;
public class TubeProbe {
    public static void main(String[] a) throws Exception {
        Fetch.setTorMode(true);
        File dir = new File(System.getenv("PREFIX") + "/tmp/orvid-tor"); dir.mkdirs();
        Tor.get().start(dir, System.getenv("PREFIX") + "/tmp/torx/jni/arm64-v8a/libtor.so", (int) ProcessHandle.current().pid());
        System.out.println("tor " + Tor.get().awaitReady(180000));
        String c = Fetch.newCircuit("tb");
        for (HtmlTube t : new HtmlTube[] {Tubes.DRTUBER, Tubes.PORNDOE, Tubes.THISVID}) {
            long t0 = System.currentTimeMillis();
            try {
                HtmlTube.Page p = Fetch.retrying(c, cc -> t.search(cc, "test", null));
                var it = p.items.get(0);
                System.out.println(t.serviceId + " search: " + p.items.size() + " next=" + p.next + " dur=" + it.getDuration() + " views=" + it.getViewCount() + " thumb=" + (it.getThumbnails().isEmpty() ? "none" : it.getThumbnails().get(0).getUrl().replaceAll("https://([^/]+)/.*", "$1")) + " " + (System.currentTimeMillis() - t0) + "ms");
                HtmlTube.Page p2 = Fetch.retrying(c, cc -> t.search(cc, "test", p.next));
                System.out.println("   page2: " + p2.items.size() + " firstDiffers=" + !p2.items.get(0).getUrl().equals(it.getUrl()));
                t0 = System.currentTimeMillis();
                if (t.web) { System.out.println("   web player: " + it.getUrl().replaceAll("https://([^/]+)/.*", "$1") + " title=" + it.getName().length()); continue; }
                HtmlTube.Media m = Fetch.retrying(c, cc -> t.resolve(cc, it.getUrl()));
                int code = Fetch.on(c, () -> { java.net.HttpURLConnection h = Fetch.open(m.mp4); h.setRequestProperty("Range", "bytes=0-1023"); h.setRequestProperty("Referer", m.referer); h.setRequestProperty("User-Agent", Fetch.USER_AGENT); return h.getResponseCode(); });
                System.out.println("   resolve: title=" + !m.title.isEmpty() + " media=" + code + " " + (System.currentTimeMillis() - t0) + "ms");
            } catch (Exception e) { System.out.println(t.serviceId + " FAIL " + e); }
        }
        Tor.get().stop(); Thread.sleep(1000); System.exit(0);
    }
}
