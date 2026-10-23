package com.orvid.yt;
import com.grack.nanojson.JsonObject;
import java.lang.reflect.*;
import java.util.*;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
public class MaskCheck {
    static String key(StreamInfoItem i) { return i.getUrl() + "|" + i.getName() + "|" + i.getUploaderName() + "|" + i.getDuration() + "|" + i.getTextualUploadDate() + "|" + i.getThumbnails().size() + "|" + i.getUploaderAvatars().size(); }
    static String mask(String cls, String name) throws Exception { Field f = Class.forName(cls).getDeclaredField(name); f.setAccessible(true); return (String) f.get(null); }
    static Related.Parse parser(Class<?> c, String name) throws Exception {
        Method m = c.getDeclaredMethod(name, String.class); m.setAccessible(true);
        return t -> { try { return (Search.Page) m.invoke(null, t); } catch (InvocationTargetException e) { throw (java.io.IOException) e.getCause(); } catch (Exception e) { throw new RuntimeException(e); } };
    }
    static Search.Page[] both(String url, Map<String, String> body, String mask, Related.Parse p) throws Exception {
        JsonObject a = new JsonObject(); a.putAll(body); JsonObject b = new JsonObject(); b.putAll(body);
        long t0 = System.currentTimeMillis();
        String full = Related.post(null, url, a, "full", null);
        long t1 = System.currentTimeMillis();
        String m = Related.post(null, url, b, "masked", mask);
        long t2 = System.currentTimeMillis();
        System.out.printf("  text %dKB -> %dKB%n", full.length() / 1024, m.length() / 1024);
        return new Search.Page[]{p.apply(full), p.apply(m)};
    }
    static void compare(String label, Search.Page[] r) {
        Map<String, String> a = new LinkedHashMap<>(), b = new LinkedHashMap<>();
        for (StreamInfoItem i : r[0].items) a.put(i.getUrl(), key(i));
        for (StreamInfoItem i : r[1].items) b.put(i.getUrl(), key(i));
        int same = 0, diff = 0, onlyA = 0, onlyB = 0;
        for (Map.Entry<String, String> e : a.entrySet()) {
            String m = b.get(e.getKey());
            if (m == null) onlyA++;
            else if (m.equals(e.getValue())) same++;
            else { diff++; System.out.println("   FIELD DIFF full:   " + e.getValue() + "\n              masked: " + m); }
        }
        for (String u : b.keySet()) if (!a.containsKey(u)) onlyB++;
        System.out.printf("%s: full %d, masked %d, shared urls identical %d, shared urls differing %d, only full %d, only masked %d, cont %s/%s, corrected %s/%s%n",
                label, a.size(), b.size(), same, diff, onlyA, onlyB, r[0].continuation != null, r[1].continuation != null, r[0].corrected, r[1].corrected);
    }
    public static void main(String[] args) throws Exception {
        String S = "https://www.youtube.com/youtubei/v1/search?prettyPrint=false", N = "https://www.youtube.com/youtubei/v1/next?prettyPrint=false";
        Related.Parse sp = parser(Search.class, "page"), rp = parser(Related.class, "toPage");
        for (String q : new String[]{"minecraft", "minecarft", "barış manço", "elraenn"}) {
            Map<String, String> body = Map.of("query", q);
            Search.Page[] r = both(S, body, mask("com.orvid.yt.Search", "FIRST_MASK"), sp);
            compare("search " + q, r);
            if (r[0].continuation != null) compare("search more " + q, both(S, Map.of("continuation", r[0].continuation), mask("com.orvid.yt.Search", "MORE_MASK"), sp));
        }
        for (String v : new String[]{"dQw4w9WgXcQ", "jNQXAC9IVRw"}) {
            Search.Page[] r = both(N, Map.of("videoId", v), mask("com.orvid.yt.Related", "NEXT_MASK"), rp);
            compare("next " + v, r);
            if (r[0].continuation != null) compare("next more " + v, both(N, Map.of("continuation", r[0].continuation), mask("com.orvid.yt.Related", "NEXT_MORE_MASK"), rp));
        }
        Search.Page p = Search.first(null, "minecraft", null);
        System.out.println("Search.first through the app path: " + p.items.size() + " items");
        System.exit(0);
    }
}
