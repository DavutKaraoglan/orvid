import com.orvid.net.*;
import com.orvid.yt.*;
public class RelatedMoreProbe {
    public static void main(String[] a) throws Exception {
        String c = Fetch.newCircuit("v");
        Visionos.ensureVisitor(c);
        Search.Page p = Related.page(c, "dQw4w9WgXcQ");
        System.out.println("page1 " + p.items.size() + " token=" + (p.continuation != null));
        for (int i = 2; i <= 4 && p.continuation != null; i++) {
            p = Related.more(c, p.continuation);
            System.out.println("page" + i + " " + p.items.size() + " token=" + (p.continuation != null) + " first=" + (p.items.isEmpty() ? "-" : p.items.get(0).getName()));
        }
    }
}
