import com.orvid.net.*;
import com.orvid.tor.Tor;
import org.schabi.newpipe.extractor.*;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.stream.*;
import java.io.*;
import java.net.*;
import java.util.*;

public class TorAppProbe {
    static long t0 = System.currentTimeMillis();
    static void log(String s) { System.out.println(String.format("%6d %s", System.currentTimeMillis() - t0, s)); }
    static String ip(String circuit) throws Exception {
        return Fetch.on(circuit, () -> new String(Fetch.bytes("https://check.torproject.org/api/ip")));
    }
    public static void main(String[] a) throws Exception {
        File dir = new File(System.getenv("PREFIX") + "/tmp/orvid-tor"); dir.mkdirs();
        Tor.get().addListener(() -> log("tor " + Tor.get().state() + " " + Tor.get().progress() + "% " + Tor.get().summary()));
        Fetch.setTorMode(true);
        NewPipe.init(Fetch.get());
        Tor.get().start(dir, System.getenv("PREFIX") + "/tmp/torx/jni/arm64-v8a/libtor.so", (int) ProcessHandle.current().pid());
        log("ready=" + Tor.get().awaitReady(180000) + " ports=" + Tor.get().ports().length);
        log("circuit A " + ip("a")); log("circuit B " + ip("b")); log("circuit A again " + ip("a"));
        String sc = Fetch.newCircuit("s");
        SearchInfo s = Fetch.retrying(sc, c -> SearchInfo.getInfo(ServiceList.YouTube, ServiceList.YouTube.getSearchQHFactory().fromQuery("barış manço", Collections.emptyList(), "")));
        log("search ok " + s.getRelatedItems().size());
        String url = null;
        for (InfoItem i : s.getRelatedItems()) if (i instanceof StreamInfoItem && ((StreamInfoItem) i).getDuration() > 0) { url = i.getUrl(); break; }
        String vc = Fetch.newCircuit("v"); final String u = url;
        StreamInfo info = Fetch.retrying(vc, c -> StreamInfo.getInfo(ServiceList.YouTube, u));
        log("stream ok " + info.getName());
        LocalProxy p = new LocalProxy();
        String master = p.wrap(info.getHlsUrl(), vc);
        HttpURLConnection c = (HttpURLConnection) new URL(master).openConnection(Proxy.NO_PROXY);
        String m = new String(c.getInputStream().readAllBytes());
        String variant = null; for (String l : m.split("\n")) if (l.startsWith("http")) variant = l;
        String v = new String(((HttpURLConnection) new URL(variant).openConnection(Proxy.NO_PROXY)).getInputStream().readAllBytes());
        String seg = null; for (String l : v.split("\n")) if (l.startsWith("http")) { seg = l; break; }
        HttpURLConnection sc2 = (HttpURLConnection) new URL(seg).openConnection(Proxy.NO_PROXY);
        byte[] b = sc2.getInputStream().readAllBytes();
        log("segment via proxy+tor " + sc2.getResponseCode() + " " + b.length / 1024 + "KB first=0x" + Integer.toHexString(b[0] & 0xff));
        System.out.println(Diag.dump().lines().filter(x -> x.contains("blocked") || x.contains("proxy")).reduce("", (x, y) -> x + y + "\n"));
        Tor.get().stop();
        Thread.sleep(1000);
        log("stopped; process alive? " + (ProcessHandle.allProcesses().anyMatch(ph -> ph.info().commandLine().orElse("").contains("orvid-tor"))));
        Fetch.setTorMode(false);
    }
}
