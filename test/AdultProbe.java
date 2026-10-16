import com.orvid.net.*;
import com.orvid.tor.Tor;
import java.io.File;
public class AdultProbe {
    interface S { Object run(String c) throws Exception; }
    static Object t(String name, String c, S s) {
        long t0 = System.currentTimeMillis();
        try { Object r = Fetch.retrying(c, s::run); System.out.println(name + ": ok " + (System.currentTimeMillis() - t0) + "ms"); return r; }
        catch (Exception e) { System.out.println(name + ": FAIL " + e + " " + (System.currentTimeMillis() - t0) + "ms"); return null; }
    }
    static String info(java.util.List<org.schabi.newpipe.extractor.stream.StreamInfoItem> items, String next) {
        if (items.isEmpty()) return "0 items";
        var it = items.get(0);
        return items.size() + " items next=" + next + " dur=" + it.getDuration() + " views=" + it.getViewCount() + " title=" + !it.getName().isEmpty() + " thumb=" + !it.getThumbnails().isEmpty();
    }
    public static void main(String[] a) throws Exception {
        Fetch.setTorMode(true);
        File dir = new File(System.getenv("PREFIX") + "/tmp/orvid-tor"); dir.mkdirs();
        Tor.get().start(dir, System.getenv("PREFIX") + "/tmp/torx/jni/arm64-v8a/libtor.so", (int) ProcessHandle.current().pid());
        System.out.println("tor " + Tor.get().awaitReady(180000));
        String c = Fetch.newCircuit("ad");
        var tx = (com.orvid.adult.Txxx.Page) t("txxx search", c, cc -> com.orvid.adult.Txxx.search(cc, "test", null));
        if (tx != null) { System.out.println("  " + info(tx.items, tx.next)); var m = (com.orvid.adult.Txxx.Media) t("txxx resolve", c, cc -> com.orvid.adult.Txxx.resolve(cc, tx.items.get(0).getUrl())); if (m != null) System.out.println("  mp4=" + (m.mp4 != null) + " title=" + !m.title.isEmpty()); }
        var rg = (com.orvid.adult.RedGifs.Page) t("redgifs search", c, cc -> com.orvid.adult.RedGifs.search(cc, "test", null));
        if (rg != null) { System.out.println("  " + info(rg.items, rg.next)); var m = (com.orvid.adult.RedGifs.Media) t("redgifs resolve", c, cc -> com.orvid.adult.RedGifs.resolve(cc, rg.items.get(0).getUrl())); if (m != null) System.out.println("  mp4=" + (m.mp4 != null)); }
        var po = (com.orvid.adult.PornOne.Page) t("pornone search", c, cc -> com.orvid.adult.PornOne.search(cc, "test", null));
        if (po != null) { System.out.println("  " + info(po.items, po.next)); var m = (com.orvid.adult.PornOne.Media) t("pornone resolve", c, cc -> com.orvid.adult.PornOne.resolve(cc, po.items.get(0).getUrl())); if (m != null) System.out.println("  mp4=" + (m.mp4 != null) + " title=" + !m.title.isEmpty()); }
        Tor.get().stop(); Thread.sleep(1000); System.exit(0);
    }
}
