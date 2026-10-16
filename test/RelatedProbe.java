import com.orvid.net.*;
import com.orvid.yt.*;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
public class RelatedProbe {
    public static void main(String[] a) throws Exception {
        String c = Fetch.newCircuit("v");
        Visionos.fetch(c, "dQw4w9WgXcQ");
        long t = System.currentTimeMillis();
        java.util.List<StreamInfoItem> items = Related.fetch(c, "dQw4w9WgXcQ");
        System.out.println(items.size() + " items " + (System.currentTimeMillis() - t) + "ms");
        for (StreamInfoItem i : items.subList(0, Math.min(4, items.size()))) System.out.println("  " + i.getName() + " | " + i.getUploaderName() + " | " + i.getDuration() + "s | " + i.getTextualUploadDate() + " | " + i.getThumbnails().size());
        System.out.print(Diag.dump());
    }
}
