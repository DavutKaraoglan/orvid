import com.orvid.search.*;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import java.io.*;
import java.util.*;
public class RankBench {
    static boolean junk(Query q, StreamInfoItem it) {
        String t = Query.fold(it.getName() == null ? "" : it.getName());
        boolean hit = false;
        String ch = Query.fold(it.getUploaderName() == null ? "" : it.getUploaderName());
        for (String term : q.terms) if (t.contains(term) || ch.contains(term)) hit = true;
        if (!hit) return true;
        String name = it.getName() == null ? "" : it.getName();
        boolean cyr = name.codePoints().anyMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.CYRILLIC);
        boolean queryCyr = q.terms.stream().anyMatch(s -> s.codePoints().anyMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.CYRILLIC));
        if (cyr && !queryCyr) return true;
        return it.getViewCount() >= 0 && it.getViewCount() < 1000;
    }
    public static void main(String[] a) throws Exception {
        String dir = System.getenv("PREFIX") + "/tmp/orvid-probe/mix/";
        int junkTotal = 0, ytFirstTotal = 0, n = 0;
        StringBuilder rows = new StringBuilder();
        for (File f : Objects.requireNonNull(new File(dir).listFiles())) {
            if (!f.getName().endsWith(".ser")) continue;
            List<List<StreamInfoItem>> lists;
            try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(f))) { lists = (List<List<StreamInfoItem>>) in.readObject(); }
            String query = f.getName().replace(".ser", "").replace('_', ' ');
            if (query.startsWith("bar ")) query = "barış manço";
            double[] w = new double[lists.size()]; Arrays.fill(w, 0.6); w[0] = 1;
            Query q = Query.parse(query);
            List<StreamInfoItem> r = Ranker.rank(q, lists, w);
            Set<StreamInfoItem> yt = Collections.newSetFromMap(new IdentityHashMap<>());
            yt.addAll(lists.get(0));
            int junk = 0, firstYt = -1;
            for (int i = 0; i < Math.min(10, r.size()); i++) if (junk(q, r.get(i))) junk++;
            for (int i = 0; i < r.size(); i++) if (yt.contains(r.get(i))) { firstYt = i + 1; break; }
            junkTotal += junk; ytFirstTotal += firstYt; n++;
            rows.append(String.format("%s\t%d\t%d%n", query, junk, firstYt));
        }
        System.out.print(rows);
        System.out.printf("TOTAL\tjunk=%d\tqueries=%d\tavgFirstYouTube=%.1f%n", junkTotal, n, ytFirstTotal / (double) n);
    }
}
