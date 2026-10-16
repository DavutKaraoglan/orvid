import com.orvid.net.*;
import com.orvid.tor.Tor;
import java.io.File;
import java.nio.file.*;
public class EpProbe {
    public static void main(String[] a) throws Exception {
        Fetch.setTorMode(true);
        File dir = new File(System.getenv("PREFIX") + "/tmp/orvid-tor"); dir.mkdirs();
        Tor.get().start(dir, System.getenv("PREFIX") + "/tmp/torx/jni/arm64-v8a/libtor.so", (int) ProcessHandle.current().pid());
        System.out.println("tor " + Tor.get().awaitReady(180000));
        String url = null;
        String c = Fetch.newCircuit("e");
        long t0 = System.currentTimeMillis();
        try {
            com.orvid.ep.Eporner.Page p = Fetch.retrying(c, cc -> com.orvid.ep.Eporner.search(cc, "test", null));
            System.out.println("run search: " + p.items.size() + " next=" + p.next + " dur=" + p.items.get(0).getDuration() + " thumb=" + !p.items.get(0).getThumbnails().isEmpty() + " " + (System.currentTimeMillis() - t0) + "ms");
            t0 = System.currentTimeMillis();
            com.orvid.ep.Eporner.Media m = Fetch.retrying(c, cc -> com.orvid.ep.Eporner.resolve(cc, p.items.get(0).getUrl()));
            System.out.println("run resolve: mp4=" + m.mp4.replaceAll(".*-(\\d+p)\\.mp4", "$1") + " title=" + !m.title.isEmpty() + " " + (System.currentTimeMillis() - t0) + "ms");
        } catch (Exception e) { System.out.println("run FAIL " + e); }
        Tor.get().stop(); Thread.sleep(1000); System.exit(0);
    }
}
