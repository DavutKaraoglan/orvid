import com.orvid.net.*;
import com.orvid.tor.Tor;
import com.orvid.yt.Visionos;
import com.orvid.play.Dash;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import java.io.*;
import java.util.*;
public class VqProbe {
    public static void main(String[] a) throws Exception {
        Fetch.setTorMode(true);
        File dir = new File(System.getenv("PREFIX") + "/tmp/orvid-tor"); dir.mkdirs();
        Tor.get().start(dir, System.getenv("PREFIX") + "/tmp/torx/jni/arm64-v8a/libtor.so", (int) ProcessHandle.current().pid());
        System.out.println("tor " + Tor.get().awaitReady(180000));
        List<String> ids = new ArrayList<>();
        for (String f : a) {
            List<List<StreamInfoItem>> lists;
            try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(new File(System.getenv("PREFIX") + "/tmp/orvid-probe/few/" + f + ".ser")))) { lists = (List<List<StreamInfoItem>>) in.readObject(); }
            int n = 0;
            for (StreamInfoItem it : lists.get(0)) { String id = Visionos.videoId(it.getUrl()); if (id != null && n++ < 4) ids.add(id); }
        }
        for (String id : ids) {
            try {
                Visionos.Video v = Visionos.fetch(Fetch.newCircuit("vq"), id);
                int maxShort = 0, avcShort = 0, vp9Short = 0, noRange = 0; boolean vertical = false;
                for (Visionos.Format f : v.formats) {
                    if (!f.isVideo()) continue;
                    int s = Math.min(f.width, f.height);
                    if (f.initRange == null || f.indexRange == null) { noRange++; continue; }
                    maxShort = Math.max(maxShort, s);
                    if (f.codec().startsWith("avc1")) avcShort = Math.max(avcShort, s);
                    if (f.codec().startsWith("vp09") || f.codec().equals("vp9")) vp9Short = Math.max(vp9Short, s);
                    if (f.height > f.width) vertical = true;
                }
                StringBuilder pick = new StringBuilder();
                for (int vp9Max : new int[]{0, 1080, 2160}) {
                    int best = 0;
                    for (Visionos.Format f : Dash.videoTracks(v.formats, 2160, vp9Max)) best = Math.max(best, Math.min(f.width, f.height));
                    pick.append(" vp9Max").append(vp9Max).append("->").append(best).append('p');
                }
                System.out.printf("%s %s best=%dp avc=%dp vp9=%dp noRange=%d |%s | %s%n", id, vertical ? "V" : "H", maxShort, avcShort, vp9Short, noRange, pick, v.title.length() > 40 ? v.title.substring(0, 40) : v.title);
            } catch (Exception e) { System.out.println(id + " FAIL " + e); }
        }
        Tor.get().stop(); Thread.sleep(300); System.exit(0);
    }
}
