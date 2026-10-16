import com.orvid.net.*;
import com.orvid.tor.Tor;
import com.orvid.yt.Visionos;
import java.io.File;

public class VisionosProbe {
    public static void main(String[] a) throws Exception {
        boolean tor = a.length > 0 && a[0].equals("tor");
        if (tor) {
            Fetch.setTorMode(true);
            File dir = new File(System.getenv("PREFIX") + "/tmp/orvid-tor"); dir.mkdirs();
            Tor.get().start(dir, System.getenv("PREFIX") + "/tmp/torx/jni/arm64-v8a/libtor.so", (int) ProcessHandle.current().pid());
            System.out.println("tor ready " + Tor.get().awaitReady(180000));
        }
        for (String id : new String[]{"dQw4w9WgXcQ", "jNQXAC9IVRw", "kJQP7kiw5Fk"}) {
            String c = Fetch.newCircuit("v");
            long t = System.currentTimeMillis();
            try {
                Visionos.Video v = Visionos.fetch(c, id);
                long ms = System.currentTimeMillis() - t;
                StringBuilder q = new StringBuilder();
                for (Visionos.Format f : v.formats) if (f.isVideo() && f.codec().startsWith("avc1")) q.append(f.height).append(' ');
                System.out.println(id + " " + ms + "ms '" + v.title + "' " + v.author + " views=" + v.views + " len=" + v.lengthSeconds + " hls=" + (v.hls != null) + " avc=" + q);
            } catch (Exception e) {
                System.out.println(id + " FAIL " + e);
            }
        }
        System.out.print(Diag.dump());
        if (tor) Tor.get().stop();
    }
}
