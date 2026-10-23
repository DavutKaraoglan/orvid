package com.orvid.yt;
import com.orvid.net.*;
import com.orvid.tor.Tor;
import java.io.*;
import java.net.HttpURLConnection;
public class SegProbe {
    static final int SEG = 100 * 1024, N = 20;
    static long separate(String c, String url, long from) throws Exception {
        long t = System.currentTimeMillis();
        for (int i = 0; i < N; i++) {
            long a = from + (long) i * SEG;
            Fetch.on(c, () -> { HttpURLConnection h = Fetch.open(url); h.setRequestProperty("Range", "bytes=" + a + "-" + (a + SEG - 1)); h.setRequestProperty("Accept-Encoding", "identity"); try (InputStream in = h.getInputStream()) { in.readAllBytes(); } return null; });
        }
        return System.currentTimeMillis() - t;
    }
    static long single(String c, String url, long from) throws Exception {
        long t = System.currentTimeMillis();
        Fetch.on(c, () -> { HttpURLConnection h = Fetch.open(url); h.setRequestProperty("Range", "bytes=" + from + "-" + (from + (long) SEG * N - 1)); h.setRequestProperty("Accept-Encoding", "identity"); try (InputStream in = h.getInputStream()) { byte[] b = new byte[SEG]; int got = 0; for (int n; (n = in.read(b)) > 0; ) got += n; } return null; });
        return System.currentTimeMillis() - t;
    }
    public static void main(String[] a) throws Exception {
        Fetch.setTorMode(true);
        File dir = new File(System.getenv("PREFIX") + "/tmp/orvid-tor"); dir.mkdirs();
        Tor.get().start(dir, System.getenv("PREFIX") + "/tmp/torx/jni/arm64-v8a/libtor.so", (int) ProcessHandle.current().pid());
        System.out.println("tor " + Tor.get().awaitReady(240000));
        for (String id : new String[]{"dQw4w9WgXcQ", "FtutLA63Cp8", "9bZkp7q19f0"}) {
            String c = Fetch.newCircuit("seg" + id);
            Visionos.Video v = Visionos.fetch(c, id);
            Visionos.Format best = null;
            for (Visionos.Format f : v.formats) if (f.url != null && f.mime != null && f.mime.startsWith("video/webm") && f.height == 360 && f.contentLength > 9_500_000L) best = f;
            if (best == null) { System.out.println(id + " no 360p"); continue; }
            for (int r = 0; r < 2; r++) {
                long from = 300_000L + r * 2_200_000L;
                long s1 = r == 0 ? separate(c, best.url, from) : single(c, best.url, from);
                long s2 = r == 0 ? single(c, best.url, from + 4_400_000L) : separate(c, best.url, from + 4_400_000L);
                long sep = r == 0 ? s1 : s2, one = r == 0 ? s2 : s1;
                System.out.printf("%s itag %d: 20 x 100KB separate %d ms, one 2MB request %d ms%n", id, best.itag, sep, one);
            }
        }
        Tor.get().stop(); Thread.sleep(300); System.exit(0);
    }
}
