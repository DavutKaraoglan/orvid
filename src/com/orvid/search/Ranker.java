package com.orvid.search;

import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Ranker {

    private static final double LOOSE = -2;
    private static final int MIN_STRICT = 3;

    private Ranker() {
    }

    public static final class Scored {
        public final StreamInfoItem item;
        public final double score;
        final int order;

        Scored(StreamInfoItem item, double score, int order) {
            this.item = item;
            this.score = score;
            this.order = order;
        }
    }

    public static List<StreamInfoItem> rank(Query q, List<List<StreamInfoItem>> sources, double[] weights) {
        return rank(q, sources, weights, null);
    }

    public static List<StreamInfoItem> rank(Query q, List<List<StreamInfoItem>> sources, double[] weights, boolean[] tagged) {
        Context ctx = context(q, blend(sources, 8));
        anchorPerSource(q, ctx, sources);
        popularity(ctx, sources);
        List<Scored> all = new ArrayList<>();
        int[] strictBySource = new int[sources.size()];
        List<List<Scored>> looseBySource = new ArrayList<>();
        for (int k = 0; k < sources.size(); k++) looseBySource.add(new ArrayList<Scored>());
        int order = 0;
        for (int src = 0; src < sources.size(); src++) {
            List<StreamInfoItem> list = sources.get(src);
            for (int rank = 0; rank < list.size(); rank++) {
                StreamInfoItem item = list.get(rank);
                double s = score(q, ctx, item, rank, weights[src]);
                if (s >= 0) {
                    all.add(new Scored(item, s, order++));
                    strictBySource[src]++;
                } else if (s != Double.NEGATIVE_INFINITY) {
                    looseBySource.get(src).add(new Scored(item, s, order++));
                }
            }
        }
        Collections.sort(all, (a, b) -> a.score != b.score ? Double.compare(b.score, a.score) : a.order - b.order);
        List<StreamInfoItem> out = diversify(all);
        List<Scored> loose = new ArrayList<>();
        for (int k = 0; k < sources.size(); k++) {
            boolean primary = weights[k] >= 1;
            boolean byTags = tagged != null && k < tagged.length && tagged[k];
            if (strictBySource[k] < MIN_STRICT && (byTags || (primary && out.size() < MIN_STRICT))) {
                loose.addAll(looseBySource.get(k));
            }
        }
        Collections.sort(loose, (a, b) -> a.order - b.order);
        for (Scored s : loose) out.add(s.item);
        return collapse(out);
    }

    static void popularity(Context ctx, List<List<StreamInfoItem>> sources) {
        for (List<StreamInfoItem> list : sources) {
            List<StreamInfoItem> counted = new ArrayList<>();
            for (StreamInfoItem item : list) if (item.getViewCount() > 0) counted.add(item);
            if (counted.size() < 3) continue;
            counted.sort((a, b) -> Long.compare(a.getViewCount(), b.getViewCount()));
            for (int i = 0; i < counted.size(); i++) {
                ctx.popularity.put(counted.get(i), (i + 1.0) / counted.size());
            }
        }
    }

    private static final java.util.regex.Pattern NOISE = java.util.regex.Pattern.compile(
            "\\b(?:hd|fhd|uhd|4k|8k|1080p|720p|480p|360p|full|video|official|new)\\b");

    static String duplicateKey(String title) {
        String t = Query.fold(title == null ? "" : title);
        t = NOISE.matcher(t).replaceAll(" ");
        return t.replaceAll("[^\\p{L}\\p{N}]+", "");
    }

    static List<StreamInfoItem> collapse(List<StreamInfoItem> items) {
        List<StreamInfoItem> out = new ArrayList<>(items.size());
        java.util.Map<String, List<StreamInfoItem>> seen = new java.util.HashMap<>();
        for (StreamInfoItem item : items) {
            String key = duplicateKey(item.getName());
            if (key.length() < 12) {
                out.add(item);
                continue;
            }
            List<StreamInfoItem> same = seen.computeIfAbsent(key, k -> new ArrayList<>());
            boolean duplicate = false;
            for (StreamInfoItem other : same) {
                if (sameLength(other, item, key.length())) {
                    duplicate = true;
                    break;
                }
            }
            if (duplicate) continue;
            same.add(item);
            out.add(item);
        }
        return out;
    }

    private static boolean sameLength(StreamInfoItem a, StreamInfoItem b, int keyLength) {
        long x = a.getDuration();
        long y = b.getDuration();
        if (x <= 0 || y <= 0) return keyLength >= 25;
        return Math.abs(x - y) <= Math.max(3, Math.min(x, y) / 100);
    }

    static List<StreamInfoItem> blend(List<List<StreamInfoItem>> sources, int perSource) {
        List<StreamInfoItem> out = new ArrayList<>();
        for (int r = 0; r < perSource; r++) {
            for (List<StreamInfoItem> list : sources) if (r < list.size()) out.add(list.get(r));
        }
        return out;
    }

    static List<StreamInfoItem> diversify(List<Scored> sorted) {
        List<Scored> pool = new ArrayList<>(sorted);
        List<StreamInfoItem> out = new ArrayList<>(pool.size());
        while (!pool.isEmpty()) {
            int pick = 0;
            int n = out.size();
            if (n >= 2) {
                int a = out.get(n - 1).getServiceId();
                int b = out.get(n - 2).getServiceId();
                if (a == b && pool.get(0).item.getServiceId() == a) {
                    double best = pool.get(0).score;
                    for (int i = 1; i < Math.min(pool.size(), 8); i++) {
                        Scored s = pool.get(i);
                        if (s.item.getServiceId() != a && s.score >= best * 0.7 - 1) {
                            pick = i;
                            break;
                        }
                    }
                }
            }
            out.add(pool.remove(pick).item);
        }
        return out;
    }

    static void anchorPerSource(Query q, Context ctx, List<List<StreamInfoItem>> sources) {
        java.util.Set<String> anchored = new java.util.HashSet<>();
        boolean any = false;
        for (List<StreamInfoItem> list : sources) {
            int n = Math.min(list.size(), 15);
            if (n < 4) continue;
            any = true;
            for (String term : q.terms) {
                int hit = 0;
                for (int i = 0; i < n; i++) {
                    StreamInfoItem item = list.get(i);
                    List<String> words = Query.words(Query.fold(item.getName() == null ? "" : item.getName()));
                    List<String> channel = Query.words(Query.fold(item.getUploaderName() == null ? "" : item.getUploaderName()));
                    if (contains(words, term) || contains(channel, term) || Query.acronym(term, words)) hit++;
                }
                if (hit * 5 >= n * 3) anchored.add(term);
            }
        }
        if (!any) return;
        ctx.anchored.clear();
        ctx.anchored.addAll(anchored);
    }

    static final class Context {
        final java.util.Set<String> anchored = new java.util.HashSet<>();
        final List<String> topic = new ArrayList<>();
        final java.util.Set<String> trustedChannels = new java.util.HashSet<>();
        final java.util.Map<StreamInfoItem, Double> popularity = new java.util.IdentityHashMap<>();
    }

    static Context context(Query q, List<StreamInfoItem> primary) {
        Context c = new Context();
        int n = Math.min(primary.size(), 24);
        if (n == 0) {
            c.anchored.addAll(q.terms);
            return c;
        }
        java.util.Map<String, Integer> df = new java.util.HashMap<>();
        int[] covered = new int[q.terms.size()];
        for (int i = 0; i < n; i++) {
            StreamInfoItem item = primary.get(i);
            List<String> words = Query.words(Query.fold(item.getName() == null ? "" : item.getName()));
            List<String> channel = Query.words(Query.fold(item.getUploaderName() == null ? "" : item.getUploaderName()));
            boolean any = false;
            for (int t = 0; t < q.terms.size(); t++) {
                String term = q.terms.get(t);
                if (contains(words, term) || contains(channel, term) || Query.acronym(term, words)) {
                    covered[t]++;
                    any = true;
                }
            }
            if (any && item.getUploaderName() != null) c.trustedChannels.add(Query.fold(item.getUploaderName()));
            for (String w : new java.util.HashSet<>(words)) {
                if (w.length() < 3 || q.terms.contains(w) || Query.isStop(w)) continue;
                df.merge(w, 1, Integer::sum);
            }
        }
        for (int t = 0; t < q.terms.size(); t++) {
            if (covered[t] * 5 >= n * 3) c.anchored.add(q.terms.get(t));
        }
        List<java.util.Map.Entry<String, Integer>> common = new ArrayList<>(df.entrySet());
        common.sort((a, b) -> b.getValue() - a.getValue());
        for (java.util.Map.Entry<String, Integer> e : common) {
            if (c.topic.size() >= 6 || e.getValue() * 5 < n) break;
            c.topic.add(e.getKey());
        }
        return c;
    }

    static double score(Query q, Context ctx, StreamInfoItem item, int rank, double weight) {
        String title = Query.fold(item.getName() == null ? "" : item.getName());
        String channel = Query.fold(item.getUploaderName() == null ? "" : item.getUploaderName());
        List<String> titleWords = Query.words(title);
        List<String> channelWords = Query.words(channel);

        for (String x : q.excluded) {
            if (contains(titleWords, x) || contains(channelWords, x)) return Double.NEGATIVE_INFINITY;
        }
        if (q.channel != null && !channel.contains(q.channel)) return Double.NEGATIVE_INFINITY;
        for (String p : q.phrases) {
            if (!title.contains(p) && !channel.contains(p)) return Double.NEGATIVE_INFINITY;
        }

        double s = 0;
        int matched = 0;
        int anchoredMatched = 0;
        for (String t : q.terms) {
            boolean inTitle = contains(titleWords, t) || Query.acronym(t, titleWords);
            boolean inChannel = contains(channelWords, t);
            if (inTitle) s += 3;
            if (inChannel) s += 2;
            if (inTitle || inChannel) {
                matched++;
                if (ctx.anchored.contains(t)) anchoredMatched++;
            }
        }
        int topicHits = 0;
        for (String w : ctx.topic) if (titleWords.contains(w)) topicHits++;
        s += Math.min(3, topicHits) * 1.5;
        if (!q.terms.isEmpty()) {
            int need = Math.max(1, (ctx.anchored.size() + 1) / 2);
            boolean trusted = ctx.trustedChannels.contains(channel);
            if (!ctx.anchored.isEmpty() && anchoredMatched < need && !trusted && topicHits < 2) {
                return LOOSE - (matched * 0.1);
            }
            if (matched == q.terms.size()) s += 3;
            String joined = String.join(" ", q.terms);
            if (q.terms.size() > 1 && title.contains(joined)) s += 4;
        }
        s += q.phrases.size() * 4;
        s += 6.0 * weight / (1 + rank * 0.35);
        Double popular = ctx.popularity.get(item);
        if (popular != null) {
            s += popular * 2.0;
        } else {
            long views = item.getViewCount();
            if (views > 0) s += Math.log10(views + 1) * 0.35;
        }
        return s;
    }

    private static boolean contains(List<String> words, String term) {
        for (String w : words) if (Query.wordMatches(term, w)) return true;
        return false;
    }

    private static final java.util.Set<String> TITLE_NOISE = new java.util.HashSet<>(java.util.Arrays.asList(
            "official", "video", "videos", "music", "audio", "lyrics", "lyric", "full", "hd", "4k", "8k", "hq", "uhd",
            "live", "new", "latest", "part", "episode", "ep", "ft", "feat", "vs", "remix", "mv", "trailer", "teaser",
            "clip", "song", "songs", "cover", "version", "edit", "shorts", "short", "best", "top", "vlog", "mix",
            "compilation", "original", "extended", "reaction", "review", "explained", "highlights", "season",
            "resmi", "klip", "sarki", "canli", "bolum", "tam", "yeni", "izle", "film", "dizi", "fragman",
            "youtube", "tiktok", "free", "watch", "you", "your", "this", "that", "from", "about", "what", "why",
            "is", "are", "was", "my", "me", "we", "it", "at", "by", "or", "be", "not", "all", "one", "day", "time"));

    public static List<String> keyTerms(List<String> terms) {
        List<String> out = new ArrayList<>();
        for (String t : terms) {
            if (t.length() < 3 || t.chars().allMatch(Character::isDigit) || TITLE_NOISE.contains(t)) continue;
            out.add(t);
        }
        return out;
    }

    private static final java.util.Set<String> ENDINGS = new java.util.HashSet<>(java.util.Arrays.asList(
            "s", "es", "ler", "lar", "in", "nin", "un", "nun", "i", "u", "e", "a", "de", "da"));

    static boolean sameWord(String term, String word) {
        if (word.equals(term)) return true;
        String longer = word.length() > term.length() ? word : term;
        String shorter = longer == word ? term : word;
        return shorter.length() >= 4 && longer.startsWith(shorter) && ENDINGS.contains(longer.substring(shorter.length()));
    }

    public static List<StreamInfoItem> similar(String title, String channel, String selfUrl,
                                               List<StreamInfoItem> next, List<StreamInfoItem> search) {
        return similar(title, channel, selfUrl, next, search, false);
    }

    public static List<StreamInfoItem> similar(String title, String channel, String selfUrl,
                                               List<StreamInfoItem> next, List<StreamInfoItem> search,
                                               boolean trustNext) {
        return similar(title, channel, selfUrl, next, search, trustNext, 0);
    }

    public static List<StreamInfoItem> similar(String title, String channel, String selfUrl,
                                               List<StreamInfoItem> next, List<StreamInfoItem> search,
                                               boolean trustNext, int minimum) {
        List<String> keys = keyTerms(Query.parse(title).terms);
        int needed = keys.size() >= 3 ? 2 : 1;
        String ownChannel = Query.fold(channel == null ? "" : channel);
        List<Scored> all = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        seen.add(selfUrl);
        int order = 0;
        List<StreamInfoItem> weak = new ArrayList<>();
        List<List<StreamInfoItem>> sources = java.util.Arrays.asList(next, search);
        double[] weights = {1.0, 0.8};
        for (int src = 0; src < sources.size(); src++) {
            List<StreamInfoItem> list = sources.get(src);
            boolean trusted = src == 0 && trustNext;
            for (int rank = 0; rank < list.size(); rank++) {
                StreamInfoItem item = list.get(rank);
                if (!seen.add(item.getUrl()) || NotInterested.hides(item)) continue;
                List<String> words = Query.words(Query.fold(item.getName() == null ? "" : item.getName()));
                int overlap = 0;
                for (String t : keys) {
                    for (String w : words) {
                        if (sameWord(t, w)) {
                            overlap++;
                            break;
                        }
                    }
                }
                boolean sameChannel = !ownChannel.isEmpty()
                        && ownChannel.equals(Query.fold(item.getUploaderName() == null ? "" : item.getUploaderName()));
                if (!sameChannel && overlap < (trusted ? 1 : needed)) {
                    weak.add(item);
                    continue;
                }
                double s = overlap * 3 + (sameChannel ? 4 : 0) + 4.0 * weights[src] / (1 + rank * 0.3);
                long views = item.getViewCount();
                if (views > 0) s += Math.log10(views + 1) * 0.25;
                all.add(new Scored(item, s, order++));
            }
        }
        Collections.sort(all, (a, b) -> a.score != b.score ? Double.compare(b.score, a.score) : a.order - b.order);
        List<StreamInfoItem> out = new ArrayList<>(all.size());
        for (Scored s : all) out.add(s.item);
        for (int i = 0; out.size() < minimum && i < weak.size(); i++) out.add(weak.get(i));
        return out;
    }

    public static List<int[]> highlights(Query q, String title) {
        List<int[]> spans = new ArrayList<>();
        if (title == null || q.terms.isEmpty()) return spans;
        String folded = Query.fold(title);
        if (folded.length() != title.length()) return spans;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("[\\p{L}\\p{N}]+").matcher(folded);
        while (m.find()) {
            for (String t : q.terms) {
                if (Query.wordMatches(t, m.group())) {
                    spans.add(new int[] {m.start(), m.end()});
                    break;
                }
            }
        }
        return spans;
    }
}
