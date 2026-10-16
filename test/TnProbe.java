import com.orvid.net.*;
import com.orvid.tor.Tor;
import java.io.File;
public class TnProbe {
    public static void main(String[] a) throws Exception {
        Fetch.setTorMode(true);
        File dir = new File(System.getenv("PREFIX") + "/tmp/orvid-tor"); dir.mkdirs();
        Tor.get().start(dir, System.getenv("PREFIX") + "/tmp/torx/jni/arm64-v8a/libtor.so", (int) ProcessHandle.current().pid());
        System.out.println("tor " + Tor.get().awaitReady(180000));
        String c = Fetch.newCircuit("t");
        long t0 = System.currentTimeMillis();
        com.orvid.tn.Tnaflix.Page p = Fetch.retrying(c, cc -> com.orvid.tn.Tnaflix.search(cc, "test", null));
        var it = p.items.get(0);
        System.out.println("search: " + p.items.size() + " next=" + p.next + " dur=" + it.getDuration() + " views=" + it.getViewCount() + " author=" + !it.getUploaderName().isEmpty() + " thumb=" + !it.getThumbnails().isEmpty() + " " + (System.currentTimeMillis() - t0) + "ms");
        t0 = System.currentTimeMillis();
        com.orvid.tn.Tnaflix.Media m = Fetch.retrying(c, cc -> com.orvid.tn.Tnaflix.resolve(cc, it.getUrl()));
        System.out.println("resolve: mp4=" + (m.mp4 != null) + " title=" + !m.title.isEmpty() + " " + (System.currentTimeMillis() - t0) + "ms");
        Tor.get().stop(); Thread.sleep(1000); System.exit(0);
    }
}
