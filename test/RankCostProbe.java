import com.orvid.net.*;
import com.orvid.tor.Tor;
import com.orvid.search.*;
import com.orvid.yt.Visionos;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
public class RankCostProbe {
    public static void main(String[] a) throws Exception {
        Fetch.setTorMode(true);
        Source.adultUnlocked = true;
        File dir = new File(System.getenv("PREFIX") + "/tmp/orvid-tor"); dir.mkdirs();
        Tor.get().start(dir, System.getenv("PREFIX") + "/tmp/torx/jni/arm64-v8a/libtor.so", (int) ProcessHandle.current().pid());
        Tor.get().awaitReady(180000);
        Visionos.refill(); Thread.sleep(4000);
        String text = a.length > 0 ? a[0] : "milf";
        Query q = Query.parse(text);
        Filters f = new Filters();
        for (Source s : com.orvid.ui.Sources.EXTRA) f.set(s, true);
        Set<String> web = new HashSet<>(Arrays.asList("spankbang", "fourkporn", "jable", "missav", "ashemaletube", "boyfriendtv"));
        List<Source> srcs = new ArrayList<>();
        for (Source s : com.orvid.ui.Sources.EXTRA) if (!web.contains(s.key) && s.wanted(f, q)) srcs.add(s);
        int n = srcs.size() + 1;
        List<List<StreamInfoItem>> lists = new ArrayList<>();
        for (int i = 0; i < n; i++) lists.add(new ArrayList<>());
        List<Integer> order = Collections.synchronizedList(new ArrayList<>());
        ExecutorService pool = Executors.newFixedThreadPool(16);
        List<Future<?>> fs = new ArrayList<>();
        fs.add(pool.submit(() -> { try { lists.set(0, com.orvid.yt.Search.first(Visionos.takeCircuit(), q.platformQuery, f.youtubeParams()).items); order.add(0); } catch (Exception e) {} }));
        for (int i = 0; i < srcs.size(); i++) {
            final int k = i + 1; final Source s = srcs.get(i);
            fs.add(pool.submit(() -> { try { lists.set(k, Hedge.call(Fetch.newCircuit("x"), () -> Fetch.newCircuit("x"), 2500, c -> s.search(c, q.platformQuery, f, null)).value.items); order.add(k); } catch (Exception e) {} }));
        }
        for (Future<?> x : fs) try { x.get(120, TimeUnit.SECONDS); } catch (Exception e) {}
        int total = 0; for (var l : lists) total += l.size();
        System.out.println("sources " + order.size() + "/" + n + " total results " + total);
        double[] w = new double[n]; Arrays.fill(w, 1.0);
        for (int round = 0; round < 2; round++) {
            List<List<StreamInfoItem>> arrived = new ArrayList<>();
            for (int i = 0; i < n; i++) arrived.add(new ArrayList<>());
            long sum = 0, max = 0; int calls = 0; StringBuilder trace = new StringBuilder();
            for (int k : order) {
                arrived.set(k, lists.get(k));
                long t = System.nanoTime();
                List<StreamInfoItem> r = f.arrange(q, arrived, w, null);
                long ms = (System.nanoTime() - t) / 1_000_000;
                sum += ms; max = Math.max(max, ms); calls++;
                if (calls % 8 == 0 || calls == order.size()) trace.append(" ").append(calls).append(":").append(r.size()).append("=").append(ms).append("ms");
            }
            System.out.println("round " + round + ": " + calls + " calls, total " + sum + "ms, longest " + max + "ms |" + trace);
        }
        Tor.get().stop(); Thread.sleep(300); System.exit(0);
    }
}
