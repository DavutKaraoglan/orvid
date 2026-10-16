import com.orvid.net.*;
public class PgProbe {
    public static void main(String[] a) throws Exception {
        String port = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(System.getenv("PREFIX") + "/tmp/torport"))).trim();
        java.net.Proxy proxy = new java.net.Proxy(java.net.Proxy.Type.HTTP, new java.net.InetSocketAddress("127.0.0.1", Integer.parseInt(port)));
        java.net.HttpURLConnection h = (java.net.HttpURLConnection) new java.net.URL("https://www.porngo.com/search/test/").openConnection(proxy);
        h.setRequestProperty("User-Agent", Fetch.USER_AGENT);
        String html = new String(h.getInputStream().readAllBytes());
        String[] cards = html.split(java.util.regex.Pattern.quote("class=\"thumb item"));
        System.out.println("len " + html.length() + " cards " + (cards.length - 1));
        for (int i = 1; i < Math.min(cards.length, 3); i++) {
            String b = cards[i];
            for (String p : new String[] {"href=\"(https://www\\.porngo\\.com/videos/\\d+/[^\"]+)\"", "thumb__title\"><span>([^<]+)<", "<img src=\"([^\"]+)\"", "thumb__duration\">([^<]+)<"}) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile(p).matcher(b);
                System.out.println(i + " " + p.substring(0, Math.min(20, p.length())) + " -> " + (m.find() ? "ok" : "NO"));
            }
        }
    }
}
