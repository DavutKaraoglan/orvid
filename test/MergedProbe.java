import com.orvid.net.*;
import com.orvid.tor.Tor;
import com.orvid.yt.*;
import com.orvid.dm.Dailymotion;
import com.orvid.search.*;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;
public class MergedProbe {
    public static void main(String[] a) throws Exception {
        Fetch.setTorMode(true);
        File dir = new File(System.getenv("PREFIX") + "/tmp/orvid-tor"); dir.mkdirs();
        Tor.get().start(dir, System.getenv("PREFIX") + "/tmp/torx/jni/arm64-v8a/libtor.so", (int) ProcessHandle.current().pid());
        System.out.println("tor ready " + Tor.get().awaitReady(180000));
        Visionos.refill(); Thread.sleep(8000);
        ExecutorService pool = Executors.newCachedThreadPool();
        Filters f = new Filters();
        for (int run = 0; run < 3; run++) {
            String input = new String[]{"barış manço", "how to cook rice", "\"take on me\" -live"}[run];
            if (run == 1) { f.duration = Filters.DURATION_LONG; }
            if (run == 2) { f.duration = Filters.DURATION_ANY; f.sort = Filters.SORT_VIEWS; }
            Query q = Query.parse(input);
            long t = System.currentTimeMillis();
            Future<Dailymotion.Page> dm = pool.submit(() -> Dailymotion.search(Visionos.takeCircuit(), q.platformQuery, 1, f.dailymotionParams()));
            Hedge.Result<Search.Page> yt = Hedge.call(Visionos.takeCircuit(), Visionos::takeCircuit, 1500, c -> Search.first(c, q.platformQuery, f.youtubeParams()));
            long tYt = System.currentTimeMillis() - t;
            List<StreamInfoItem> dmItems = new ArrayList<>();
            try { dmItems = dm.get().items; } catch (Exception e) { System.out.println("dm fail " + e); }
            long tAll = System.currentTimeMillis() - t;
            List<StreamInfoItem> out = f.arrange(q, Arrays.asList(yt.value.items, dmItems), new double[]{1.0, 0.7});
            System.out.println(input + " dur=" + f.duration + " sort=" + f.sort + ": yt " + yt.value.items.size() + " in " + tYt + "ms, all " + tAll + "ms, shown " + out.size() + "/" + (yt.value.items.size() + dmItems.size()));
            for (StreamInfoItem i : out.subList(0, Math.min(4, out.size()))) System.out.println("   " + (i.getServiceId() == 100 ? "[dm] " : "") + i.getName() + " | " + i.getDuration() + "s | " + i.getViewCount());
        }
        System.out.println(Diag.dump().lines().filter(l -> l.contains("hedge")).count() + " hedged");
        Tor.get().stop();
        System.exit(0);
    }
}
