package com.orvid.ext;
import com.orvid.net.*;
import com.orvid.tor.Tor;
import com.orvid.search.Source;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import java.io.*;
import java.util.*;
public class HedgeAB {
    public static void main(String[] a) throws Exception {
        File base = new File(System.getenv("PREFIX") + "/tmp/orvid-probe/extroot2");
        Registry.reload(base);
        Fetch.setTorMode(true);
        File dir = new File(System.getenv("PREFIX") + "/tmp/orvid-tor"); dir.mkdirs();
        Tor.get().start(dir, System.getenv("PREFIX") + "/tmp/torx/jni/arm64-v8a/libtor.so", (int) ProcessHandle.current().pid());
        System.out.println("tor " + Tor.get().awaitReady(180000));
        List<String> urls = new ArrayList<>();
        Map<String, Integer> per = new HashMap<>();
        for (String f : a) {
            List<List<StreamInfoItem>> lists;
            try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(new File(System.getenv("PREFIX") + "/tmp/orvid-probe/few/" + f + ".ser")))) { lists = (List<List<StreamInfoItem>>) in.readObject(); }
            for (List<StreamInfoItem> l : lists) for (StreamInfoItem it : l) {
                ExtSource s = Registry.forUrl(it.getUrl());
                if (s == null || s.adult) continue;
                if (per.merge(s.key, 1, Integer::sum) > 3) continue;
                urls.add(it.getUrl());
            }
        }
        Collections.shuffle(urls, new Random(7));
        long[] sum = new long[2]; int[] ok = new int[2];
        List<Long>[] times = new List[]{new ArrayList<Long>(), new ArrayList<Long>()};
        int k = 0;
        for (String u : urls) {
            ExtSource s = Registry.forUrl(u);
            for (int pass = 0; pass < 2; pass++) {
                int mode = (k + pass) % 2;
                long delay = mode == 0 ? 2500 : 0;
                long t = System.currentTimeMillis();
                String res;
                try {
                    Hedge.call(Fetch.newCircuit("p"), () -> Fetch.newCircuit("p"), delay, c -> s.resolve(c, u));
                    long took = System.currentTimeMillis() - t; ok[mode]++; times[mode].add(took); res = "OK " + took;
                } catch (Exception e) { res = "FAIL " + (System.currentTimeMillis() - t) + " " + e.getClass().getSimpleName(); }
                System.out.println((mode == 0 ? "hedge2500 " : "failonly  ") + s.key + " " + res);
            }
            k++;
        }
        for (int m = 0; m < 2; m++) { Collections.sort(times[m]); System.out.println((m == 0 ? "hedge2500" : "failonly ") + " ok=" + ok[m] + " median=" + (times[m].isEmpty() ? -1 : times[m].get(times[m].size() / 2)) + " p90=" + (times[m].isEmpty() ? -1 : times[m].get(times[m].size() * 9 / 10)) + " all=" + times[m]); }
        Tor.get().stop(); Thread.sleep(300); System.exit(0);
    }
}
