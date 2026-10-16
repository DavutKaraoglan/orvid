import com.orvid.net.*;
import com.orvid.tor.Tor;
import java.io.File;
public class SuggestProbe {
    public static void main(String[] a) throws Exception {
        Fetch.setTorMode(true);
        File dir = new File(System.getenv("PREFIX") + "/tmp/orvid-tor"); dir.mkdirs();
        Tor.get().start(dir, System.getenv("PREFIX") + "/tmp/torx/jni/arm64-v8a/libtor.so", (int) ProcessHandle.current().pid());
        System.out.println("tor " + Tor.get().awaitReady(180000));
        for (String q : new String[] {"blender tut", "lofi hip", "barış man"}) {
            long t0 = System.currentTimeMillis();
            try { System.out.println(q + " -> " + com.orvid.yt.Suggest.fetch(q) + " " + (System.currentTimeMillis() - t0) + "ms"); }
            catch (Exception e) { System.out.println(q + " FAIL " + e); }
        }
        Tor.get().stop(); Thread.sleep(1000); System.exit(0);
    }
}
