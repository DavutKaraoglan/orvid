import com.orvid.net.*;
import com.orvid.yt.*;
import com.orvid.search.*;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import java.util.*;
public class BulkProbe {
    public static void main(String[] a) throws Exception {
        String c = Fetch.newCircuit("s");
        Visionos.ensureVisitor(c);
        for (String query : a) {
            Search.Page p = Search.first(c, query, null);
            Query q = Query.parse(query);
            List<StreamInfoItem> ranked = Ranker.rank(q, Arrays.asList(p.items, new ArrayList<>()), new double[]{1.0, 0.7});
            System.out.println("### " + query + "  (kept " + ranked.size() + "/" + p.items.size() + ")");
            for (int i = 0; i < Math.min(8, ranked.size()); i++) System.out.println("  + " + cut(ranked.get(i).getName()) + "  | " + cut(ranked.get(i).getUploaderName()));
            for (StreamInfoItem i : p.items) if (!ranked.contains(i)) System.out.println("  - " + cut(i.getName()) + "  | " + cut(i.getUploaderName()));
        }
    }
    static String cut(String s) { return s == null ? "" : s.length() > 70 ? s.substring(0, 70) : s; }
}
