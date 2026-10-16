import com.orvid.net.*;
import com.orvid.tor.Tor;
import java.io.File;
import java.net.HttpURLConnection;
import java.nio.file.*;
public class DachProbe {
    static String get(String circuit, String url, boolean range) throws Exception {
        return Fetch.on(circuit, () -> {
            HttpURLConnection c = Fetch.open(url);
            c.setRequestProperty("User-Agent", Fetch.USER_AGENT);
            if (range) c.setRequestProperty("Range", "bytes=0-100000");
            int code = c.getResponseCode();
            byte[] b = (code < 400 ? c.getInputStream() : c.getErrorStream()).readAllBytes();
            return code + " " + (range ? b.length + "B" : new String(b));
        });
    }
    public static void main(String[] a) throws Exception {
        Fetch.setTorMode(true);
        String pre = System.getenv("PREFIX") + "/tmp/";
        File main = new File(pre + "orvid-tor"); main.mkdirs();
        String bin = pre + "torx/jni/arm64-v8a/libtor.so";
        int pid = (int) ProcessHandle.current().pid();
        Tor.get().start(main, bin, pid);
        File dach = new File(pre + "orvid-dach"); dach.mkdirs();
        Tor.dach().prepare(dach, bin, pid, () -> {
            try {
                new File(dach, "data").mkdirs();
                Files.copy(Paths.get("res/raw/geoip_dach"), new File(dach, "geoip").toPath(), StandardCopyOption.REPLACE_EXISTING);
                new File(dach, "geoip6").createNewFile();
                for (String n : new String[]{"cached-certs", "cached-microdesc-consensus", "cached-microdescs"}) {
                    File f = new File(main, "data/" + n);
                    if (f.isFile()) Files.copy(f.toPath(), new File(dach, "data/" + n).toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception e) { throw new RuntimeException(e); }
        });
        System.out.println("main tor " + Tor.get().awaitReady(180000));
        String U = "https://nrodlzdf-a.akamaihd.net/dach/tivi/17/05/170522_katzen_sdg_pur/8/170522_katzen_sdg_pur_2328k_p35v14.mp4";
        String normal = Fetch.newCircuit("p");
        System.out.println("normal ip  " + get(normal, "https://check.torproject.org/api/ip", false));
        System.out.println("normal zdf " + get(normal, U, true));
        System.out.println("dach state before: " + Tor.dach().state());
        long t0 = System.currentTimeMillis();
        String d = Fetch.dachCircuit();
        System.out.println("dach ip    " + get(d, "https://check.torproject.org/api/ip", false) + " " + (System.currentTimeMillis() - t0) + "ms");
        System.out.println("dach zdf   " + get(d, U, true));
        System.out.println("dach part  " + get(d + "#p1", U, true));
        Tor.dach().stop(); Tor.get().stop(); Thread.sleep(500); System.exit(0);
    }
}
