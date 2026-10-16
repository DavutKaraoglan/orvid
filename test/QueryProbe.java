import com.orvid.net.*;
import com.orvid.yt.*;
import com.orvid.search.*;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import java.util.*;
public class QueryProbe {
    public static void main(String[] a) throws Exception {
        String c = Fetch.newCircuit("s");
        Visionos.ensureVisitor(c);
        Search.Page p = Search.first(c, a[0], null);
        Query q = Query.parse(a[0]);
        System.out.println("YouTube order:");
        for (StreamInfoItem i : p.items) System.out.println("  " + i.getName() + " | " + i.getUploaderName());
        System.out.println("Ranked:");
        for (StreamInfoItem i : Ranker.rank(q, Arrays.asList(p.items, new ArrayList<>()), new double[]{1.0, 0.7})) System.out.println("  " + i.getName());
    }
}
