import com.orvid.net.*;
import com.orvid.tor.Tor;
import java.io.File;
public class XhProbe2 {
    public static void main(String[] a) throws Exception {
        Fetch.setTorMode(true);
        File dir = new File(System.getenv("PREFIX") + "/tmp/orvid-tor"); dir.mkdirs();
        Tor.get().start(dir, System.getenv("PREFIX") + "/tmp/torx/jni/arm64-v8a/libtor.so", (int) ProcessHandle.current().pid());
        System.out.println("tor " + Tor.get().awaitReady(180000));
        for (int i = 0; i < 3; i++) {
            String c = Fetch.newCircuit("xh" + i);
            try {
                var p = com.orvid.xh.XHamster.search(c, "milf", null);
                System.out.println("#" + i + " items=" + p.items.size() + " next=" + p.next);
            } catch (Exception e) { System.out.println("#" + i + " FAIL " + e); }
        }
        Tor.get().stop(); Thread.sleep(300); System.exit(0);
    }
}
