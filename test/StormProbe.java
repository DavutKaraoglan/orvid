import com.orvid.net.*;
import com.orvid.tor.Tor;
import com.orvid.yt.*;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;
public class StormProbe {
    public static void main(String[] a) throws Exception {
        int n = Integer.parseInt(a[0]);
        Fetch.setTorMode(true);
        File dir = new File(System.getenv("PREFIX") + "/tmp/orvid-tor"); dir.mkdirs();
        Tor.get().start(dir, System.getenv("PREFIX") + "/tmp/torx/jni/arm64-v8a/libtor.so", (int) ProcessHandle.current().pid());
        Tor.get().awaitReady(60000);
        ExecutorService p = Executors.newCachedThreadPool();
        int[] ports = Tor.get().ports();
        for (int i = 0; i < Math.min(n, ports.length); i++) {
            int port = ports[i];
            p.submit(() -> {
                try {
                    java.net.Proxy proxy = new java.net.Proxy(java.net.Proxy.Type.HTTP, new java.net.InetSocketAddress("127.0.0.1", port));
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL("https://check.torproject.org/api/ip").openConnection(proxy);
                    c.setConnectTimeout(15000); c.setReadTimeout(15000);
                    try (var in = c.getInputStream()) { in.readAllBytes(); }
                } catch (Exception ignored) {}
                return null;
            });
        }
        Thread.sleep(1000);
        long t = System.currentTimeMillis();
        String res;
        try { Search.first(Fetch.newCircuit("s"), a[1], null); res = "search " + (System.currentTimeMillis() - t) + "ms"; } catch (Exception e) { res = "search FAIL " + e.getClass().getSimpleName() + " " + (System.currentTimeMillis() - t) + "ms"; }
        t = System.currentTimeMillis();
        String sug;
        try { Suggest.fetch(a[1]); sug = "suggest " + (System.currentTimeMillis() - t) + "ms"; } catch (Exception e) { sug = "suggest FAIL " + (System.currentTimeMillis() - t) + "ms"; }
        System.out.println("warm=" + n + " ports=" + ports.length + " " + res + " " + sug);
        Tor.get().stop(); Thread.sleep(300); System.exit(0);
    }
}
