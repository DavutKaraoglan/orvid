import com.orvid.net.*;
import com.orvid.tor.Tor;
import com.orvid.play.Resolve;
import com.orvid.search.Source;
import java.io.File;
public class IssueProbe {
    public static void main(String[] a) throws Exception {
        Fetch.setTorMode(true);
        File dir = new File(System.getenv("PREFIX") + "/tmp/orvid-tor"); dir.mkdirs();
        Tor.get().start(dir, System.getenv("PREFIX") + "/tmp/torx/jni/arm64-v8a/libtor.so", (int) ProcessHandle.current().pid());
        System.out.println("tor " + Tor.get().awaitReady(180000));
        com.orvid.yt.Visionos.refill(); Thread.sleep(3000);
        for (String id : new String[]{"_4aN5x0XgV8", "6x_PRNPU4nE", "9FtP_686rCQ", "E4Z4v3qzd9A"}) {
            try {
                var v = com.orvid.yt.Visionos.fetch(com.orvid.yt.Visionos.takePlayCircuit(), id);
                System.out.println("yt " + id + " live=" + v.live + " hls=" + (v.hls != null) + " formats=" + v.formats.size() + " length=" + v.lengthSeconds);
            } catch (Exception e) { System.out.println("yt " + id + " " + e); }
        }
        for (Source s : com.orvid.ui.Sources.EXTRA) {
            if (true) continue;
            var items = s.search(Fetch.newCircuit("q"), "milf", new com.orvid.search.Filters(), null).items;
            for (int i = 0; i < 3 && i < items.size(); i++) {
                String url = items.get(i).getUrl();
                long t = System.currentTimeMillis();
                try {
                    var st = Resolve.preview(url);
                    System.out.println(s.key + " " + i + " ok " + (System.currentTimeMillis() - t) + "ms hls=" + st.direct.hls + " " + java.net.URI.create(st.direct.url).getHost());
                    var c = Fetch.on(st.circuit, () -> { var h = Fetch.open(st.direct.url); h.setRequestProperty("User-Agent", Fetch.USER_AGENT); if (st.direct.referer != null) h.setRequestProperty("Referer", st.direct.referer); h.setRequestProperty("Range", "bytes=0-1023"); return h.getResponseCode(); });
                    System.out.println("   media HTTP " + c);
                } catch (Exception e) { System.out.println(s.key + " " + i + " FAIL " + e + "  " + url); }
            }
        }
        Tor.get().stop(); Thread.sleep(300); System.exit(0);
    }
}
