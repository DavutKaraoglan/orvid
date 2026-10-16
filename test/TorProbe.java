import com.orvid.net.Fetch;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import java.net.HttpURLConnection;
import java.util.*;

public class TorProbe {
    public static void main(String[] a) throws Exception {
        Fetch.useSocks("127.0.0.1", 39050);
        NewPipe.init(Fetch.get());
        long t = System.currentTimeMillis();
        try {
            HttpURLConnection c = Fetch.open("https://check.torproject.org/api/ip");
            System.out.println("tor check: " + new String(c.getInputStream().readAllBytes()) + " " + (System.currentTimeMillis() - t) + "ms");
        } catch (Exception e) { System.out.println("tor check fail " + e); }
        List<String> urls = new ArrayList<>();
        t = System.currentTimeMillis();
        try {
            SearchInfo s = SearchInfo.getInfo(ServiceList.YouTube, ServiceList.YouTube.getSearchQHFactory().fromQuery(a.length > 0 ? a[0] : "barış manço", Collections.emptyList(), ""));
            for (InfoItem i : s.getRelatedItems()) if (i instanceof StreamInfoItem && ((StreamInfoItem) i).getDuration() > 0 && urls.size() < 4) urls.add(i.getUrl());
            System.out.println("search ok " + (System.currentTimeMillis() - t) + "ms, " + s.getRelatedItems().size() + " items");
        } catch (Exception e) { System.out.println("search FAIL " + e); System.out.println(com.orvid.net.Diag.dump()); return; }
        for (String u : urls) {
            t = System.currentTimeMillis();
            try {
                StreamInfo info = StreamInfo.getInfo(ServiceList.YouTube, u);
                String hls = info.getHlsUrl();
                int code = -1;
                if (hls != null && !hls.isEmpty()) { HttpURLConnection c = Fetch.open(hls); c.setRequestProperty("User-Agent", Fetch.USER_AGENT); code = c.getResponseCode(); }
                System.out.println("stream ok " + (System.currentTimeMillis() - t) + "ms hls=" + code + " muxed=" + info.getVideoStreams().size() + " " + info.getName());
            } catch (Exception e) { System.out.println("stream FAIL " + (System.currentTimeMillis() - t) + "ms " + e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()).substring(0, Math.min(120, String.valueOf(e.getMessage()).length()))); }
        }
    }
}
