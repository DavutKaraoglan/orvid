import com.orvid.net.*;
import com.orvid.tor.Tor;
import java.io.File;
import java.net.HttpURLConnection;
public class RawProbe {
    public static void main(String[] a) throws Exception {
        Fetch.setTorMode(true);
        File dir = new File(System.getenv("PREFIX") + "/tmp/orvid-tor"); dir.mkdirs();
        Tor.get().start(dir, System.getenv("PREFIX") + "/tmp/torx/jni/arm64-v8a/libtor.so", (int) ProcessHandle.current().pid());
        System.out.println("tor " + Tor.get().awaitReady(180000));
        String[] urls = {"https://rutube.ru/api/search/video/?query=%D0%BA%D0%BE%D1%88%D0%BA%D0%B8&page=1&format=json", "https://xhamster.com/search/milf"};
        for (String u : urls) for (int i = 0; i < 3; i++) {
            String c = Fetch.newCircuit("raw" + i);
            try {
                String r = Fetch.on(c, () -> {
                    HttpURLConnection h = Fetch.open(u);
                    h.setRequestProperty("User-Agent", Fetch.USER_AGENT);
                    h.setRequestProperty("Accept-Language", "en-US,en;q=0.8");
                    int code = h.getResponseCode();
                    byte[] b = (code < 400 ? h.getInputStream() : h.getErrorStream()).readAllBytes();
                    String s = new String(b, "UTF-8");
                    int vt = s.indexOf("videoThumbProps");
                    return code + " " + b.length + "B final=" + h.getURL() + " ctype=" + h.getContentType() + " enc=" + h.getContentEncoding() + " | " + s.substring(0, Math.min(160, s.length())).replace('\n', ' ') + (vt >= 0 ? " | vtp:" + s.substring(vt, Math.min(s.length(), vt + 60)) : "");
                });
                System.out.println(u.substring(8, 30) + " #" + i + " " + r);
            } catch (Exception e) { System.out.println(u.substring(8, 30) + " #" + i + " FAIL " + e); }
        }
        Tor.get().stop(); Thread.sleep(300); System.exit(0);
    }
}
