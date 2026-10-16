import com.orvid.net.Fetch;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.net.HttpURLConnection;
import java.util.Collections;

public class Probe {
    public static void main(String[] a) throws Exception {
        NewPipe.init(Fetch.get());
        String q = a.length > 0 ? a[0] : "lofi hip hop";
        long t = System.currentTimeMillis();
        SearchInfo s = SearchInfo.getInfo(ServiceList.YouTube,
                ServiceList.YouTube.getSearchQHFactory().fromQuery(q, Collections.emptyList(), ""));
        System.out.println("search " + (System.currentTimeMillis() - t) + "ms, items " + s.getRelatedItems().size());
        String first = null;
        for (InfoItem it : s.getRelatedItems()) {
            if (it instanceof StreamInfoItem) {
                StreamInfoItem si = (StreamInfoItem) it;
                System.out.println("  " + si.getName() + " | " + si.getUploaderName() + " | " + si.getDuration() + "s | " + si.getUrl());
                if (first == null && si.getDuration() > 0) first = si.getUrl();
            }
        }
        t = System.currentTimeMillis();
        StreamInfo info = StreamInfo.getInfo(ServiceList.YouTube, first);
        System.out.println("stream " + (System.currentTimeMillis() - t) + "ms: " + info.getName());
        for (VideoStream v : info.getVideoStreams()) {
            System.out.println("  muxed " + v.getResolution() + " " + v.getFormat() + " delivery=" + v.getDeliveryMethod());
        }
        System.out.println("  videoOnly " + info.getVideoOnlyStreams().size() + ", audio " + info.getAudioStreams().size() + ", hls=" + (info.getHlsUrl() != null && !info.getHlsUrl().isEmpty()));
        if (!info.getVideoStreams().isEmpty()) {
            HttpURLConnection c = Fetch.open(info.getVideoStreams().get(0).getContent());
            c.setRequestProperty("User-Agent", Fetch.USER_AGENT);
            c.setRequestProperty("Range", "bytes=0-65535");
            System.out.println("  first muxed range GET -> " + c.getResponseCode() + " len " + c.getContentLengthLong());
        }
    }
}
