import com.orvid.net.*;
import com.orvid.tor.Tor;
import com.orvid.yt.Visionos;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import java.io.*;
import java.net.HttpURLConnection;
import java.util.*;
import java.util.concurrent.*;
public class ThumbAB {
    public static void main(String[] a) throws Exception {
        Fetch.setTorMode(true);
        File dir = new File(System.getenv("PREFIX") + "/tmp/orvid-tor"); dir.mkdirs();
        Tor.get().start(dir, System.getenv("PREFIX") + "/tmp/torx/jni/arm64-v8a/libtor.so", (int) ProcessHandle.current().pid());
        Tor.get().awaitReady(60000);
        List<String> ids = new ArrayList<>();
        for (String f : new String[]{"minecraft", "cat", "deprem", "galatasaray", "lofi", "funny_dogs", "bad_apple", "car_crash", "python_tutorial", "iphone_17_review", "how_to_cook_rice"}) {
            List<List<StreamInfoItem>> lists;
            try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(new File(System.getenv("PREFIX") + "/tmp/orvid-probe/few/" + f + ".ser")))) { lists = (List<List<StreamInfoItem>>) in.readObject(); }
            for (StreamInfoItem it : lists.get(0)) { String id = Visionos.videoId(it.getUrl()); if (id != null && !ids.contains(id)) ids.add(id); }
        }
        String[] files = {"hq720.webp", "hqdefault.webp", "mqdefault.webp"};
        int[] circuitsVariants = {3, 8};
        Map<String, List<Long>> all = new TreeMap<>(), kb = new TreeMap<>();
        int k = 0;
        for (int round = 0; round < 3; round++) {
            for (String file : files) for (int nc : circuitsVariants) {
                String label = file + " x" + nc;
                List<String> urls = new ArrayList<>();
                for (int i = 0; i < 8; i++) urls.add("https://i.ytimg.com/vi_webp/" + ids.get(k++ % ids.size()) + "/" + file);
                String[] circ = new String[nc];
                for (int i = 0; i < nc; i++) circ[i] = Fetch.newCircuit("img" + round + file + nc + i);
                ExecutorService p = Executors.newFixedThreadPool(8);
                long t = System.currentTimeMillis();
                long[] bytes = new long[1];
                List<Future<?>> fs = new ArrayList<>();
                for (int i = 0; i < 8; i++) {
                    String u = urls.get(i), c = circ[i % nc];
                    fs.add(p.submit(() -> { int n = Fetch.on(c, () -> { HttpURLConnection h = Fetch.open(u); h.setRequestProperty("User-Agent", Fetch.USER_AGENT); try (var in = h.getInputStream()) { return in.readAllBytes().length; } }); synchronized (bytes) { bytes[0] += n; } return null; }));
                }
                for (Future<?> f : fs) try { f.get(60, TimeUnit.SECONDS); } catch (Exception e) {}
                p.shutdown();
                long ms = System.currentTimeMillis() - t;
                all.computeIfAbsent(label, x -> new ArrayList<>()).add(ms);
                kb.computeIfAbsent(label, x -> new ArrayList<>()).add(bytes[0] / 1024);
                System.out.println("round " + round + " " + label + " all8=" + ms + "ms " + bytes[0] / 1024 + "KB");
            }
        }
        for (String l : all.keySet()) { List<Long> v = all.get(l); Collections.sort(v); System.out.println("SUMMARY " + l + " median all8=" + v.get(v.size() / 2) + "ms  sizes(KB)=" + kb.get(l)); }
        Tor.get().stop(); Thread.sleep(300); System.exit(0);
    }
}
