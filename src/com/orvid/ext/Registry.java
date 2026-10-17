package com.orvid.ext;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.orvid.adult.HtmlTube;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public final class Registry {

    private static final Pattern ID = Pattern.compile("[a-z0-9_\\-]{2,40}");
    private static volatile List<ExtSource> sources = Collections.emptyList();
    private static volatile java.util.Set<String> engines = Collections.emptySet();
    private static volatile boolean loaded;

    public static boolean engineOn(String key) {
        return engines.contains(key);
    }

    public static boolean empty() {
        return sources.isEmpty() && engines.isEmpty();
    }

    public static boolean loaded() {
        return loaded;
    }

    private Registry() {
    }

    public static List<ExtSource> sources() {
        return sources;
    }

    public static ExtSource forUrl(String url) {
        for (ExtSource s : sources) if (s.handles(url)) return s;
        return null;
    }

    public static synchronized void reload(File root) {
        List<ExtSource> out = new ArrayList<>();
        java.util.Set<String> on = new java.util.HashSet<>();
        File[] repos = root.listFiles(f -> f.isDirectory() && !f.getName().startsWith("."));
        if (repos != null) {
            for (File repo : repos) {
                try {
                    out.addAll(load(repo, on));
                } catch (Exception e) {
                    com.orvid.net.Diag.mark("repo " + repo.getName() + " skipped: " + e.getMessage());
                }
            }
        }
        out.sort((a, b) -> a.adult != b.adult ? (a.adult ? 1 : -1) : Double.compare(b.weight, a.weight));
        sources = Collections.unmodifiableList(out);
        engines = Collections.unmodifiableSet(on);
        loaded = true;
    }

    static List<ExtSource> load(File repo) throws Exception {
        return load(repo, new java.util.HashSet<>());
    }

    static List<ExtSource> load(File repo, java.util.Set<String> on) throws Exception {
        JsonObject index = JsonParser.object().from(read(new File(repo, "index.json")));
        JsonArray list = index.getArray("sources");
        List<ExtSource> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            String file = list.getString(i);
            try {
                JsonObject d = JsonParser.object().from(read(new File(repo, Repos.safeName(file))));
                String engine = d.getString("engine", null);
                if (engine != null) on.add(engine);
                else out.add(source(repo, d));
            } catch (Exception e) {
                com.orvid.net.Diag.mark("source " + file + " skipped: " + e.getMessage());
            }
        }
        return out;
    }

    static ExtSource source(File repo, JsonObject d) throws Exception {
        String id = d.getString("id", "");
        if (!ID.matcher(id).matches()) throw new IOException("bad id");
        String name = d.getString("name", id);
        boolean adult = d.getBoolean("adult", false);
        double weight = Math.max(0.1, Math.min(1.0, d.getDouble("weight", 0.5)));
        Pattern match = Pattern.compile(d.getString("match"));
        int serviceId = 2000 + Math.floorMod((repo.getName() + "/" + id).hashCode(), 1_000_000);
        HtmlTube html = null;
        Script script = null;
        JsonObject h = d.getObject("html");
        if (h != null && !h.isEmpty()) {
            html = new HtmlTube(serviceId, h.getString("site"), h.getString("search"), h.getString("searchPage", h.getString("search")),
                    d.getString("match"), h.getString("card"), h.getString("href"), h.getString("title"), h.getString("thumb"),
                    h.getString("duration", null), h.getString("views", null));
            if (h.getString("iframe", null) != null) html.iframe(h.getString("iframe"));
            if (h.getString("only", null) != null) html.only(h.getString("only"));
            if (h.getString("skip", null) != null) html.skip(h.getString("skip"));
            if (h.getString("referer", null) != null) html.referer(h.getString("referer"));
            if (h.getString("linkPrefix", null) != null) html.linkPrefix(h.getString("linkPrefix"));
            if (h.getBoolean("paged", false)) html.paged();
        } else {
            String file = d.getString("script", null);
            if (file == null) throw new IOException("no html or script");
            script = new Script(id, read(new File(repo, Repos.safeName(file))));
        }
        ExtSource s = new ExtSource(repo.getName(), id, name, serviceId, weight, adult, d.getInt("version", 1),
                match, d.getBoolean("web", false), html, script);
        String icon = d.getString("icon", null);
        if (icon != null) {
            File f = new File(repo, Repos.safeName(icon));
            if (f.isFile()) s.iconFile = f.getAbsolutePath();
        }
        String lang = d.getString("lang", null);
        if (lang != null) s.script(lang);
        return s;
    }

    static String read(File f) throws IOException {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }
}
