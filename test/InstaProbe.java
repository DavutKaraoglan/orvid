package com.orvid.ext;
import com.orvid.net.*;
import com.orvid.tor.Tor;
import java.io.File;
public class InstaProbe {
    public static void main(String[] a) throws Exception {
        File base = new File(System.getenv("PREFIX") + "/tmp/orvid-probe/extroot2");
        Fetch.setTorMode(true);
        File dir = new File(System.getenv("PREFIX") + "/tmp/orvid-tor"); dir.mkdirs();
        Tor.get().start(dir, System.getenv("PREFIX") + "/tmp/torx/jni/arm64-v8a/libtor.so", (int) ProcessHandle.current().pid());
        System.out.println("tor " + Tor.get().awaitReady(180000));
        ExtSource s = null;
        for (ExtSource x : Registry.load(new File(base, "general"))) if (x.key.equals("instagram")) s = x;
        String c = Fetch.newCircuit("insta");
        long t = System.currentTimeMillis();
        var p = s.search(c, a[0], null, null);
        System.out.println("search " + (System.currentTimeMillis() - t) + "ms n=" + p.items.size());
        int ok = 0, n = Math.min(Integer.parseInt(a[1]), p.items.size());
        for (int k = 0; k < n; k++) {
            var it = p.items.get(k);
            String rc = Fetch.newCircuit("r" + k);
            t = System.currentTimeMillis();
            try {
                final ExtSource src = s; var d = Hedge.call(rc, () -> Fetch.newCircuit("spare"), 2500, cc -> src.resolve(cc, it.getUrl())).value;
                long took = System.currentTimeMillis() - t;
                final String u = d.url;
                int code = Fetch.on(rc, () -> { var h = Fetch.open(u); h.setRequestProperty("User-Agent", Fetch.USER_AGENT); h.setRequestProperty("Range", "bytes=0-65535"); int cd = h.getResponseCode(); if (cd < 400) h.getInputStream().readNBytes(65536); return cd; });
                ok++;
                System.out.println(k + " OK " + took + "ms media=" + code + " " + u.replaceAll("^https://([^/]+).*", "$1") + " | " + it.getName());
            } catch (Exception e) {
                System.out.println(k + " FAIL " + (System.currentTimeMillis() - t) + "ms " + e.getClass().getSimpleName() + " " + e.getMessage() + " | " + it.getUrl());
            }
        }
        System.out.println("ok " + ok + "/" + n); for (String l : Diag.dump().split("\n")) if (l.contains("hedge")) System.out.println(l);
        Tor.get().stop(); Thread.sleep(300); System.exit(0);
    }
}
