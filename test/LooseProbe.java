import com.orvid.search.*;
import org.schabi.newpipe.extractor.stream.*;
import java.util.*;
public class LooseProbe {
    static StreamInfoItem it(int svc, String url, String title) {
        StreamInfoItem i = new StreamInfoItem(svc, url, title, StreamType.VIDEO_STREAM);
        i.setUploaderName("ch"); i.setDuration(600); i.setViewCount(1000); return i;
    }
    public static void main(String[] a) {
        Query q = Query.parse("blender tutorial");
        List<StreamInfoItem> yt = new ArrayList<>();
        for (int i = 0; i < 5; i++) yt.add(it(0, "yt" + i, "Blender tutorial part " + i));
        StreamInfoItem x = it(0, "ytx", "Spanish singer music video"); x.setUploaderName("other"); yt.add(x);
        List<StreamInfoItem> ad = new ArrayList<>();
        for (int i = 0; i < 4; i++) ad.add(it(106, "ad" + i, "Random clip number " + i));
        List<StreamInfoItem> out = Ranker.rank(q, Arrays.asList(yt, ad), new double[] {1.0, 0.6}, new boolean[] {false, true});
        StringBuilder sb = new StringBuilder();
        for (StreamInfoItem i : out) sb.append(i.getUrl()).append(' ');
        System.out.println(sb.toString().trim());
        List<StreamInfoItem> only = Ranker.rank(q, Arrays.asList(new ArrayList<StreamInfoItem>(), ad), new double[] {1.0, 0.6}, new boolean[] {false, true});
        System.out.println("adult only: " + only.size());
    }
}
