import com.orvid.net.*;
import com.orvid.tor.Tor;
import com.orvid.search.Query;
import java.io.File;
import java.net.HttpURLConnection;
public class CancelProbe {
    public static void main(String[] a) throws Exception {
        for (String s : new String[]{"kedi videoları", "кошки", "گربه", "고양이", "ねこ", "猫", "minecraft"}) System.out.println(s + " -> " + Query.script(s));
        Fetch.setTorMode(true);
        File dir = new File(System.getenv("PREFIX") + "/tmp/orvid-tor"); dir.mkdirs();
        Tor.get().start(dir, System.getenv("PREFIX") + "/tmp/torx/jni/arm64-v8a/libtor.so", (int) ProcessHandle.current().pid());
        System.out.println("tor " + Tor.get().awaitReady(180000));
        Fetch.Scope scope = new Fetch.Scope();
        long[] t = new long[2];
        Thread worker = new Thread(() -> {
            try {
                Fetch.within(scope, () -> Hedge.call(Fetch.newCircuit("c"), () -> Fetch.newCircuit("c"), 1000, c -> Fetch.on(c, () -> {
                    HttpURLConnection h = Fetch.open("https://speed.cloudflare.com/__down?bytes=50000000");
                    byte[] buf = new byte[65536]; long n = 0; int r;
                    var in = h.getInputStream();
                    while ((r = in.read(buf)) > 0) n += r;
                    System.out.println("read ended at " + n / 1024 + "KB");
                    return n;
                })));
                System.out.println("returned normally " + (System.currentTimeMillis() - t[0]) + "ms after cancel");
            } catch (Exception e) {
                t[1] = System.currentTimeMillis();
                System.out.println("worker stopped: " + e.getClass().getSimpleName() + " " + e.getMessage() + " after cancel " + (t[1] - t[0]) + "ms");
            }
        });
        worker.start();
        Thread.sleep(6000);
        t[0] = System.currentTimeMillis();
        scope.cancel();
        worker.join(30000);
        System.out.println("worker alive after 30s: " + worker.isAlive());
        try { Fetch.within(scope, () -> Fetch.open("https://example.com")); System.out.println("open after cancel: allowed?!"); }
        catch (Exception e) { System.out.println("open after cancel: " + e.getClass().getSimpleName()); }
        Tor.get().stop(); Thread.sleep(300); System.exit(0);
    }
}
