import com.orvid.net.*;
import com.orvid.tor.Tor;
import com.orvid.yt.*;
import java.io.File;
public class SearchProbe {
    public static void main(String[] a) throws Exception {
        boolean tor = a.length > 0 && a[0].equals("tor");
        if (tor) {
            Fetch.setTorMode(true);
            File dir = new File(System.getenv("PREFIX") + "/tmp/orvid-tor"); dir.mkdirs();
            Tor.get().start(dir, System.getenv("PREFIX") + "/tmp/torx/jni/arm64-v8a/libtor.so", (int) ProcessHandle.current().pid());
            System.out.println("tor ready " + Tor.get().awaitReady(180000));
        }
        Visionos.refill();
        Thread.sleep(tor ? 6000 : 2000);
        for (String q : new String[]{"lofi hip hop", "barış manço", "how to cook rice"}) {
            String c = Visionos.takeCircuit();
            long t = System.currentTimeMillis();
            Search.Page p = Fetch.retrying(c, cc -> Search.first(cc, q));
            long t1 = System.currentTimeMillis() - t;
            t = System.currentTimeMillis();
            Search.Page p2 = Fetch.retrying(c, cc -> Search.more(cc, p.continuation));
            System.out.println(q + ": " + p.items.size() + " items " + t1 + "ms, more " + p2.items.size() + " items " + (System.currentTimeMillis() - t) + "ms; first: " + (p.items.isEmpty() ? "-" : p.items.get(0).getName()));
        }
        System.out.print(Diag.dump());
        if (tor) Tor.get().stop();
        System.exit(0);
    }
}
