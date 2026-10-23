import com.orvid.net.*;
import com.orvid.tor.Tor;
import com.orvid.yt.*;
import com.orvid.dm.Dailymotion;
import com.orvid.search.*;
import com.orvid.ui.Sources;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
public class MixProbe {
    static final int CORE = 3;
    public static void main(String[] a) throws Exception {
        File cache0 = new File(System.getenv("PREFIX") + "/tmp/orvid-probe/mix");
        boolean need = false;
        for (String input : a) need |= !new File(cache0, input.replaceAll("\\W", "_") + ".ser").exists();
        if (need) {
            Fetch.setTorMode(true);
            File dir = new File(System.getenv("PREFIX") + "/tmp/orvid-tor"); dir.mkdirs();
            Tor.get().start(dir, System.getenv("PREFIX") + "/tmp/torx/jni/arm64-v8a/libtor.so", (int) ProcessHandle.current().pid());
            System.out.println("tor " + Tor.get().awaitReady(180000));
        }
        com.orvid.ext.Registry.reload(new File(System.getenv("PREFIX") + "/tmp/orvid-probe/extroot2"));
        ExecutorService pool = Executors.newCachedThreadPool();
        Filters f = new Filters();
        File cache = new File(System.getenv("PREFIX") + "/tmp/orvid-probe/mix");
        cache.mkdirs();
        for (String input : a) {
            Query q = Query.parse(input);
            int n = CORE + Sources.current().size();
            List<List<StreamInfoItem>> lists = new ArrayList<>();
            for (int i = 0; i < n; i++) lists.add(new ArrayList<>());
            File saved = new File(cache, input.replaceAll("\\W", "_") + ".ser");
            if (saved.exists()) {
                try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(saved))) { lists = (List<List<StreamInfoItem>>) in.readObject(); }
            } else {
                List<Future<List<StreamInfoItem>>> fs = new ArrayList<>();
                fs.add(pool.submit(() -> Search.first(Fetch.newCircuit("yt"), q.platformQuery, null).items));
                fs.add(pool.submit(() -> Dailymotion.search(Fetch.newCircuit("dm"), q.platformQuery, 1, f.dailymotionParams()).items));
                fs.add(pool.submit(() -> com.orvid.vk.Vk.search(Fetch.newCircuit("vk"), q.platformQuery, null).items));
                for (int i = 0; i < Sources.current().size(); i++) {
                    Source s = Sources.current().get(i);
                    if (!s.wanted(f, q)) { fs.add(null); continue; }
                    fs.add(pool.submit(() -> s.search(Fetch.newCircuit("x" + s.key), q.platformQuery, f, null).items));
                }
                for (int i = 0; i < n; i++) {
                    if (fs.get(i) == null) continue;
                    try { lists.set(i, new ArrayList<>(fs.get(i).get(40, TimeUnit.SECONDS))); }
                    catch (Exception e) { System.out.println("fail " + name(i) + " " + e.getClass().getSimpleName()); fs.get(i).cancel(true); }
                }
                try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(saved))) { out.writeObject(lists); }
            }
            while (lists.size() < n) lists.add(new ArrayList<>());
            double[] w = new double[n]; w[0] = 1; w[1] = 0.7; w[2] = 0.8;
            boolean[] tagged = new boolean[n];
            for (int i = 0; i < Sources.current().size(); i++) { w[CORE + i] = Sources.current().get(i).weight; tagged[CORE + i] = Sources.current().get(i).adult; }
            StringBuilder counts = new StringBuilder();
            for (int i = 0; i < n; i++) if (!lists.get(i).isEmpty()) counts.append(name(i)).append('=').append(lists.get(i).size()).append(' ');
            System.out.println("### " + input + "  " + counts);
            List<StreamInfoItem> ranked = f.arrange(q, lists, w, tagged);
            Map<StreamInfoItem, Integer> src = new IdentityHashMap<>();
            for (int i = 0; i < n; i++) for (StreamInfoItem it : lists.get(i)) src.put(it, i);
            int[] kept = new int[n];
            for (StreamInfoItem it : ranked) kept[src.get(it)]++;
            StringBuilder ks = new StringBuilder("kept: ");
            for (int i = 0; i < n; i++) if (!lists.get(i).isEmpty()) ks.append(name(i)).append('=').append(kept[i]).append('/').append(lists.get(i).size()).append(' ');
            System.out.println(ks);
            if (System.getenv("DROPPED") != null) {
                Set<StreamInfoItem> in = Collections.newSetFromMap(new IdentityHashMap<>()); in.addAll(ranked);
                for (int i = 0; i < n; i++) for (StreamInfoItem it : lists.get(i)) if (!in.contains(it)) System.out.printf("DROP %-10s %s | %s%n", name(i), cut(it.getName()), cut(it.getUploaderName()));
                continue;
            }
            if (System.getenv("KEPT_ONLY") != null) continue;
            for (int i = 0; i < Math.min(Integer.getInteger("top", 30), ranked.size()); i++) {
                StreamInfoItem it = ranked.get(i);
                System.out.printf("%2d %-10s %s | %s | v=%d%n", i + 1, name(src.get(it)), cut(it.getName()), cut(it.getUploaderName()), it.getViewCount());
            }
        }
        if (need) { Tor.get().stop(); Thread.sleep(300); }
        System.exit(0);
    }
    static String name(int i) { return i == 0 ? "youtube" : i == 1 ? "dm" : i == 2 ? "vk" : Sources.current().get(i - CORE).key; }
    static String cut(String s) { return s == null ? "" : s.length() > 60 ? s.substring(0, 60) : s; }
}
