package com.orvid.ext;
import com.orvid.net.*;
import com.orvid.tor.Tor;
import java.io.File;
import java.nio.file.*;
import java.security.*;
import java.security.spec.*;
import java.util.*;
public class ExtProbe {
    public static void main(String[] a) throws Exception {
        File base = new File(System.getenv("PREFIX") + "/tmp/orvid-probe/extroot2");
        for (String repo : new String[]{"general", "secret"}) {
            byte[] idx = Files.readAllBytes(new File(base, repo + "/index.json").toPath());
            byte[] sig = Base64.getDecoder().decode(new String(Files.readAllBytes(new File(base, repo + "/index.sig").toPath())).trim());
            String key = com.grack.nanojson.JsonParser.object().from(new String(idx)).getString("key");
            Signature v = Signature.getInstance("SHA256withECDSA");
            v.initVerify(KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(key))));
            v.update(idx);
            System.out.println(repo + " signature ok=" + v.verify(sig) + " sources=" + Registry.load(new File(base, repo)).size());
        }
        if (!"direct".equals(System.getenv("NET"))) {
            Fetch.setTorMode(true);
            File dir = new File(System.getenv("PREFIX") + "/tmp/orvid-tor"); dir.mkdirs();
            Tor.get().start(dir, System.getenv("PREFIX") + "/tmp/torx/jni/arm64-v8a/libtor.so", (int) ProcessHandle.current().pid());
            System.out.println("tor " + Tor.get().awaitReady(180000));
        }
        for (int i = 0; i + 2 < a.length; i += 3) {
            ExtSource s = null;
            for (ExtSource x : Registry.load(new File(base, a[i]))) if (x.key.equals(a[i + 1])) s = x;
            String c = Fetch.newCircuit("e" + i);
            long t = System.currentTimeMillis();
            try {
                var p = s.search(c, a[i + 2], null, null);
                System.out.println(s.key + " search " + (System.currentTimeMillis() - t) + "ms n=" + p.items.size() + " next=" + p.next + " icon=" + (s.iconFile != null));
                for (int k = 0; k < Math.min(2, p.items.size()); k++) { var it = p.items.get(k); System.out.println("   " + it.getDuration() + "s v=" + it.getViewCount() + " " + it.getName() + " | " + it.getUploaderName() + " | " + it.getUrl()); }
                t = System.currentTimeMillis();
                var d = s.resolve(c, p.items.get(0).getUrl());
                System.out.println("   resolve " + (System.currentTimeMillis() - t) + "ms hls=" + d.hls + " " + d.url.substring(0, Math.min(80, d.url.length())) + " fallback=" + d.fallback + " sprite=" + (d.sprite != null));
                final String u = d.url; final String ref = d.referer; final String ck = d.cookie;
                int code = Fetch.on(c, () -> { var h = Fetch.open(u); h.setRequestProperty("User-Agent", Fetch.USER_AGENT); h.setRequestProperty("Range", "bytes=0-65535"); if (ref != null) h.setRequestProperty("Referer", ref); if (ck != null) h.setRequestProperty("Cookie", ck); int cd = h.getResponseCode(); if (cd < 400) h.getInputStream().readNBytes(65536); return cd; });
                System.out.println("   media HTTP " + code);
                System.out.println("   tags " + s.tags(c, p.items.get(0).getUrl()));
            } catch (Exception e) { System.out.println(a[i + 1] + " FAIL " + e); }
        }
        if (!"direct".equals(System.getenv("NET"))) Tor.get().stop();
        Thread.sleep(300); System.exit(0);
    }
}
