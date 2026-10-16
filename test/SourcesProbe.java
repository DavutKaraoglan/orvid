import com.orvid.net.*;
import com.orvid.tor.Tor;
import java.io.File;
public class SourcesProbe {
    interface Step { String run(String c) throws Exception; }
    static void t(String name, String c, Step s) {
        long t0 = System.currentTimeMillis();
        try { System.out.println(name + ": " + s.run(c) + " " + (System.currentTimeMillis() - t0) + "ms"); }
        catch (Exception e) { System.out.println(name + ": FAIL " + e + " " + (System.currentTimeMillis() - t0) + "ms"); }
    }
    public static void main(String[] a) throws Exception {
        Fetch.setTorMode(true);
        File dir = new File(System.getenv("PREFIX") + "/tmp/orvid-tor"); dir.mkdirs();
        Tor.get().start(dir, System.getenv("PREFIX") + "/tmp/torx/jni/arm64-v8a/libtor.so", (int) ProcessHandle.current().pid());
        System.out.println("tor " + Tor.get().awaitReady(180000));
        String c = Fetch.newCircuit("p");
        String[] first = new String[4];
        t("peertube search", c, cc -> { var p = com.orvid.pt.PeerTube.search(cc, "blender", null, true); first[0] = p.items.get(0).getUrl(); return p.items.size() + " next=" + p.next + " dur=" + p.items.get(0).getDuration(); });
        t("peertube resolve", c, cc -> { var m = com.orvid.pt.PeerTube.resolve(cc, first[0]); return "hls=" + (m.hls != null) + " mp4=" + (m.mp4 != null); });
        t("archive search", c, cc -> { var p = com.orvid.ia.Archive.search(cc, "apollo 11", null); first[1] = p.items.get(0).getUrl(); return p.items.size() + " next=" + p.next + " dur=" + p.items.get(1).getDuration(); });
        t("archive resolve", c, cc -> { var m = com.orvid.ia.Archive.resolve(cc, com.orvid.ia.Archive.itemId(first[1])); return "mp4=" + m.mp4.replaceAll("https://([^/]+)/.*", "$1"); });
        t("xv search", c, cc -> { var p = com.orvid.xv.Xvideos.search(cc, "test", null); first[2] = p.items.get(0).getUrl(); return p.items.size() + " next=" + p.next + " dur=" + p.items.get(0).getDuration() + " thumb=" + !p.items.get(0).getThumbnails().isEmpty(); });
        t("xv resolve", c, cc -> { var m = com.orvid.xv.Xvideos.resolve(cc, first[2]); return "hls=" + (m.hls != null) + " mp4=" + (m.mp4 != null); });
        t("aylo search", c, cc -> { var p = com.orvid.ay.Aylo.search(cc, "test", null); first[3] = p.items.get(0).getUrl(); return p.items.size() + " next=" + p.next + " dur=" + p.items.get(0).getDuration(); });
        t("aylo resolve", c, cc -> { var m = com.orvid.ay.Aylo.resolve(cc, first[3]); return "hls=" + (m.hls != null); });
        Tor.get().stop(); Thread.sleep(1000); System.exit(0);
    }
}
