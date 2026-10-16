import com.orvid.net.*;
import com.orvid.tor.Tor;
import java.io.File;
import java.nio.file.*;
public class AyloProbe {
    public static void main(String[] a) throws Exception {
        Fetch.setTorMode(true);
        File dir = new File(System.getenv("PREFIX") + "/tmp/orvid-tor"); dir.mkdirs();
        Tor.get().start(dir, System.getenv("PREFIX") + "/tmp/torx/jni/arm64-v8a/libtor.so", (int) ProcessHandle.current().pid());
        System.out.println("tor " + Tor.get().awaitReady(180000));
        String url = new String(Files.readAllBytes(Paths.get(System.getenv("PREFIX") + "/tmp/phurl"))).trim();
        for (int i = 1; i <= 3; i++) {
            String c = Fetch.newCircuit("a" + i);
            long t0 = System.currentTimeMillis();
            try {
                Hedge.Result<com.orvid.ay.Aylo.Media> r = Hedge.call(c, () -> Fetch.newCircuit("b" + System.nanoTime()), 2500, cc -> com.orvid.ay.Aylo.resolve(cc, url));
                System.out.println("run " + i + ": ok " + (System.currentTimeMillis() - t0) + "ms");
            } catch (Exception e) {
                System.out.println("run " + i + ": FAIL " + e.getMessage() + " " + (System.currentTimeMillis() - t0) + "ms");
            }
        }
        Tor.get().stop(); Thread.sleep(1000); System.exit(0);
    }
}
