import com.orvid.net.*;
import com.orvid.tor.Tor;
import com.orvid.search.Query;
import java.io.File;
import java.util.*;
public class TypeSim {
    static final long KEY_MS = 180;
    static String guessFor(String typed, List<String> found) {
        String g = null;
        for (String s : found) if (Query.fold(s).equals(Query.fold(typed))) g = s;
        if (g == null) for (String s : found) if (Query.fold(s).startsWith(Query.fold(typed))) { g = s; break; }
        return g;
    }
    public static void main(String[] a) throws Exception {
        Fetch.setTorMode(true);
        File dir = new File(System.getenv("PREFIX") + "/tmp/orvid-tor"); dir.mkdirs();
        Tor.get().start(dir, System.getenv("PREFIX") + "/tmp/torx/jni/arm64-v8a/libtor.so", (int) ProcessHandle.current().pid());
        Tor.get().awaitReady(60000);
        com.orvid.yt.Suggest.warm();
        for (String target : a) {
            long typingEnd = target.length() * KEY_MS;
            long t0 = System.currentTimeMillis();
            long hitAt = -1;
            while (true) {
                long now = System.currentTimeMillis() - t0;
                int chars = (int) Math.min(target.length(), now / KEY_MS + 1);
                String typed = target.substring(0, chars).trim();
                if (typed.length() < 2) { Thread.sleep(20); continue; }
                List<String> found = com.orvid.yt.Suggest.fetch(typed);
                long back = System.currentTimeMillis() - t0;
                int charsNow = (int) Math.min(target.length(), back / KEY_MS + 1);
                String nowText = target.substring(0, charsNow).trim();
                String g = guessFor(nowText, found);
                if (g != null && Query.fold(g).equals(Query.fold(target))) { hitAt = back; break; }
                if (chars == target.length() && charsNow == target.length()) break;
            }
            long s = System.currentTimeMillis();
            Fetch.on(Fetch.newCircuit("s"), () -> com.orvid.yt.Search.first(Fetch.newCircuit("s2"), target, null));
            long search = System.currentTimeMillis() - s;
            List<String> one = com.orvid.yt.Suggest.fetch(target);
            boolean debounceHit = guessFor(target, one) != null && Query.fold(guessFor(target, one)).equals(Query.fold(target));
            System.out.printf("%-26s typing ends %5d ms | throttle: correct guess at %6s ms -> results at %6s ms | old debounce: results at ~%d ms (hit=%b) | search %d ms%n",
                target, typingEnd, hitAt < 0 ? "-" : String.valueOf(hitAt), hitAt < 0 ? "-" : String.valueOf(hitAt + search),
                typingEnd + 150 + 400 + search, debounceHit, search);
        }
        Tor.get().stop(); Thread.sleep(300); System.exit(0);
    }
}
