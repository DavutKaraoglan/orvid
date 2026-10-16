import com.orvid.search.*;
import org.schabi.newpipe.extractor.stream.*;
import java.util.*;
public class RankProbe {
    static StreamInfoItem it(String t, String c) { StreamInfoItem i = new StreamInfoItem(0, "u" + t, t, StreamType.VIDEO_STREAM); i.setUploaderName(c); return i; }
    public static void main(String[] a) {
        String[] qs = {"barış manço gülpembe", "BARIS MANCONUN sarkilari", "\"dönence\" -canlı", "lofi kanal:lofigirl", "how to cook rice site:dailymotion"};
        for (String s : qs) {
            Query q = Query.parse(s);
            System.out.println(s + " -> platform='" + q.platformQuery + "' terms=" + q.terms + " phrases=" + q.phrases + " excl=" + q.excluded + " channel=" + q.channel + " site=" + q.site);
        }
        Query q = Query.parse("barış manço gülpembe");
        List<StreamInfoItem> yt = Arrays.asList(it("Barış Manço - Gülpembe", "Barış Manço"), it("Cem Karaca - Resimdeki Gözyaşları", "Cem Karaca"), it("En güzel 80ler şarkıları", "Nostalji"), it("Mix - Türkçe Pop", "YouTube"), it("Barış Manço'nun en iyileri", "Arşiv TV"), it("Gülpembe (cover)", "Ayşe"));
        List<StreamInfoItem> dm = Arrays.asList(it("gulpembe baris manco canli", "dmuser"), it("Funny cats", "cats"));
        for (StreamInfoItem i : Ranker.rank(q, Arrays.asList(yt, dm), new double[]{1.0, 0.7})) System.out.println("  " + i.getName() + " | " + i.getUploaderName() + "  hl=" + Ranker.highlights(q, i.getName()).size());
        Query q2 = Query.parse("\"dönence\" -canlı");
        for (StreamInfoItem i : Ranker.rank(q2, Arrays.asList(Arrays.asList(it("Barış Manço - Dönence", "x"), it("Dönence canlı", "y"), it("Gülpembe", "z"))), new double[]{1.0})) System.out.println("  q2: " + i.getName());
    }
}
