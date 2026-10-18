package com.orvid.search;
import com.orvid.net.*;
import com.orvid.tor.Tor;
import com.orvid.yt.*;
import com.orvid.dm.Dailymotion;
import com.orvid.ui.Sources;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
public class FewProbe {
    static final int CORE = 3;
    public static void main(String[] a) throws Exception {
        File cache = new File(System.getenv("PREFIX") + "/tmp/orvid-probe/few");
        cache.mkdirs();
        com.orvid.ext.Registry.reload(new File(System.getenv("PREFIX") + "/tmp/orvid-probe/extroot2"));
        boolean need = false;
        for (String input : a) need |= !new File(cache, input.replaceAll("\\W", "_") + ".ser").exists();
        if (need) {
            Fetch.setTorMode(true);
            File dir = new File(System.getenv("PREFIX") + "/tmp/orvid-tor"); dir.mkdirs();
            Tor.get().start(dir, System.getenv("PREFIX") + "/tmp/torx/jni/arm64-v8a/libtor.so", (int) ProcessHandle.current().pid());
            System.out.println("tor " + Tor.get().awaitReady(180000));
        }
        ExecutorService pool = Executors.newCachedThreadPool();
        Filters f = new Filters();
        int n = CORE + Sources.current().size();
        int[] tot = new int[7];
        for (String input : a) {
            Query q = Query.parse(input);
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
                    catch (Exception e) { fs.get(i).cancel(true); }
                }
                try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(saved))) { out.writeObject(lists); }
            }
            double[] w = new double[n]; w[0] = 1; w[1] = 0.7; w[2] = 0.8;
            boolean[] tagged = new boolean[n];
            for (int i = 0; i < Sources.current().size(); i++) { w[CORE + i] = Sources.current().get(i).weight; tagged[CORE + i] = Sources.current().get(i).adult; }
            List<List<StreamInfoItem>> kept = new ArrayList<>();
            int raw = 0, junk = 0;
            for (List<StreamInfoItem> list : lists) {
                List<StreamInfoItem> k = new ArrayList<>();
                for (StreamInfoItem it : list) { raw++; if (f.accepts(it)) k.add(it); else junk++; }
                kept.add(k);
            }
            Ranker.Context ctx = Ranker.context(q, Ranker.blend(kept, 8));
            Ranker.anchorPerSource(q, ctx, kept);
            Ranker.popularity(ctx, kept);
            int anchorDrop = 0, literalDrop = 0, hard = 0;
            StringBuilder ex = new StringBuilder();
            Map<String, int[]> per = new TreeMap<>();
            for (int src = 0; src < n; src++) {
                boolean literal = w[src] < 1 && !tagged[src];
                for (int r = 0; r < kept.get(src).size(); r++) {
                    StreamInfoItem it = kept.get(src).get(r);
                    double s = Ranker.score(q, ctx, it, r, w[src]);
                    String why = null;
                    if (s == Double.NEGATIVE_INFINITY) { hard++; why = "hard"; }
                    else if (s < 0) { anchorDrop++; why = "anchor"; }
                    else if (literal && !Ranker.mentions(q, it)) { literalDrop++; why = "literal"; }
                    int[] c = per.computeIfAbsent(name(src), k -> new int[3]);
                    c[0]++; if (why != null) c[1]++;
                    if (why != null && ex.length() < 2500) ex.append("   ").append(why).append(' ').append(name(src)).append(": ").append(cut(it.getName())).append(" | ").append(cut(it.getUploaderName())).append('\n');
                }
            }
            List<StreamInfoItem> ranked = f.arrange(q, lists, w, tagged);
            java.util.Set<StreamInfoItem> keptSet = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            keptSet.addAll(ranked);
            StringBuilder stop = new StringBuilder("   STOP:");
            for (int i = 0; i < n; i++) {
                List<StreamInfoItem> l = lists.get(i);
                if (l.size() < 5) continue;
                int c = 0; for (StreamInfoItem it : l) if (keptSet.contains(it)) c++;
                if (c * 10 < l.size()) { stop.append(' ').append(name(i)).append(' ').append(c).append('/').append(l.size()); tot[6]++; }
            }
            System.out.println(stop);
            if (System.getenv("TOP") != null) for (int i = 0; i < Math.min(12, ranked.size()); i++) System.out.println("   " + (i + 1) + " " + cut(ranked.get(i).getName()) + " | " + cut(ranked.get(i).getUploaderName()) + " | svc " + ranked.get(i).getServiceId());
            System.out.printf("### %-22s raw=%d junk=%d anchor=%d literal=%d hard=%d -> shown=%d  anchored=%s%n", input, raw, junk, anchorDrop, literalDrop, hard, ranked.size(), ctx.anchored);
            StringBuilder ps = new StringBuilder("   ");
            for (Map.Entry<String, int[]> e : per.entrySet()) if (e.getValue()[1] > 0) ps.append(e.getKey()).append(' ').append(e.getValue()[0] - e.getValue()[1]).append('/').append(e.getValue()[0]).append("  ");
            System.out.println(ps);
            if (System.getenv("EX") != null) System.out.print(ex);
            tot[0] += raw; tot[1] += junk; tot[2] += anchorDrop; tot[3] += literalDrop; tot[4] += hard; tot[5] += ranked.size();
        }
        System.out.println("stopped " + tot[6]); System.out.printf("TOTAL raw=%d junk=%d anchor=%d literal=%d hard=%d shown=%d%n", tot[0], tot[1], tot[2], tot[3], tot[4], tot[5]);
        if (need) { Tor.get().stop(); Thread.sleep(300); }
        System.exit(0);
    }
    static String name(int i) { return i == 0 ? "youtube" : i == 1 ? "dm" : i == 2 ? "vk" : Sources.current().get(i - CORE).key; }
    static String cut(String s) { return s == null ? "" : s.length() > 55 ? s.substring(0, 55) : s; }
}
