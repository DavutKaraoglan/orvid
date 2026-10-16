import com.orvid.net.*;
import com.orvid.tor.Tor;
import com.orvid.dm.Dailymotion;
import java.io.*;
import java.net.*;
public class DmProbe {
    public static void main(String[] a) throws Exception {
        boolean tor = a.length > 0 && a[0].equals("tor");
        if (tor) {
            Fetch.setTorMode(true);
            File dir = new File(System.getenv("PREFIX") + "/tmp/orvid-tor"); dir.mkdirs();
            Tor.get().start(dir, System.getenv("PREFIX") + "/tmp/torx/jni/arm64-v8a/libtor.so", (int) ProcessHandle.current().pid());
            System.out.println("tor ready " + Tor.get().awaitReady(180000));
        }
        String c = Fetch.newCircuit("d");
        long t = System.currentTimeMillis();
        Dailymotion.Page p = Dailymotion.search(c, "lofi hip hop", 1);
        System.out.println("search " + p.items.size() + " items " + (System.currentTimeMillis() - t) + "ms more=" + p.hasMore + " first=" + p.items.get(0).getName());
        t = System.currentTimeMillis();
        Dailymotion.Video v = Dailymotion.resolve(c, p.items.get(0).getUrl().replaceAll(".*/", ""));
        System.out.println("resolve " + (System.currentTimeMillis() - t) + "ms '" + v.title + "' by " + v.author);
        String code = Fetch.on(c, () -> {
            HttpURLConnection h = Fetch.open(v.hls);
            h.setRequestProperty("User-Agent", Fetch.USER_AGENT);
            for (java.util.Map.Entry<String, String> e : Dailymotion.unblockHeaders().entrySet()) h.setRequestProperty(e.getKey(), e.getValue());
            return h.getResponseCode() + " " + new String(h.getInputStream().readAllBytes()).split("\n").length + " lines";
        });
        System.out.println("manifest " + code);
        if (tor) Tor.get().stop();
        System.exit(0);
    }
}
