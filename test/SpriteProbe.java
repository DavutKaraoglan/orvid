import com.orvid.net.*;
import com.orvid.tor.Tor;
import java.io.File;
public class SpriteProbe {
    public static void main(String[] a) throws Exception {
        Fetch.setTorMode(true);
        File dir = new File(System.getenv("PREFIX") + "/tmp/orvid-tor"); dir.mkdirs();
        Tor.get().start(dir, System.getenv("PREFIX") + "/tmp/torx/jni/arm64-v8a/libtor.so", (int) ProcessHandle.current().pid());
        System.out.println("tor " + Tor.get().awaitReady(180000));
        String c = Fetch.newCircuit("sp");
        var xv = Fetch.retrying(c, cc -> com.orvid.xv.Xvideos.search(cc, "milf", null)).items.get(0).getUrl();
        var mx = Fetch.retrying(c, cc -> com.orvid.xv.Xvideos.resolve(cc, xv));
        System.out.println("xv sprite " + (mx.sprite != null) + " " + com.orvid.play.Resolve.class.getName());
        for (int i = 0; i < 3; i++) {
            try { var d = com.orvid.play.Resolve.preview(xv); System.out.println("xv direct sprite " + (d != null && d.direct.sprite != null)); break; }
            catch (Exception e) { System.out.println("xv preview try " + i + " " + e); }
        }
        var xh = Fetch.retrying(c, cc -> com.orvid.xh.XHamster.search(cc, "milf", null)).items.get(0).getUrl();
        try { var mh = Fetch.retrying(c, cc -> com.orvid.xh.XHamster.resolve(cc, xh)); System.out.println("xh sprite " + (mh.sprite != null)); }
        catch (Exception e) { System.out.println("xh FAIL " + e); }
        Tor.get().stop(); Thread.sleep(300); System.exit(0);
    }
}
