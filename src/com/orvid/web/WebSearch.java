package com.orvid.web;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.orvid.net.Diag;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public final class WebSearch {

    public interface Prompter {
        void show(WebView view, String site);

        void hide(WebView view);
    }

    public static volatile Prompter prompter;

    static final String CIRCUIT = "websearch";
    private static final Semaphore SLOTS = new Semaphore(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final String CHECK = "(function(){var t=document.title||'';"
            + "var ch=/Just a moment|Attention Required|Bir dakika|Checking/i.test(t)||!!document.querySelector('#challenge-form,#cf-challenge-running,.cf-turnstile,#challenge-stage');"
            + "var ts=!!document.querySelector('iframe[src*=\"challenges.cloudflare.com\"]');"
            + "return (ch?'C':'-')+(ts?'T':'-')+(document.readyState=='complete'?'R':'-');})()";

    private WebSearch() {
    }

    public static List<StreamInfoItem> run(Context context, int serviceId, String site, String url, String pattern)
            throws IOException {
        try {
            if (!SLOTS.tryAcquire(60, TimeUnit.SECONDS)) throw new IOException("busy");
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
        try {
            return load(context.getApplicationContext(), serviceId, site, url, pattern);
        } finally {
            SLOTS.release();
        }
    }

    private static List<StreamInfoItem> load(Context context, int serviceId, String site, String url, String pattern)
            throws IOException {
        CountDownLatch done = new CountDownLatch(1);
        List<StreamInfoItem> out = new ArrayList<>();
        String[] failure = new String[1];
        long began = System.currentTimeMillis();
        TorBrowser.openSession(context, CIRCUIT, Executors.newSingleThreadExecutor(), new TorBrowser.Ready() {
            WebView view;
            boolean prompted;
            long passedAt;
            int lastCount = -1;
            int sameCount;

            int retries;

            @Override
            public void ready(WebView v) {
                view = v;
                v.setTag((TorBrowser.ErrorListener) (wv, failed, reason) -> {
                    if (retries >= 2 || view == null) return;
                    retries++;
                    Diag.mark("web search " + site + " retry after " + reason);
                    TorBrowser.reroute(context, CIRCUIT, Executors.newSingleThreadExecutor(), () -> {
                        if (view != null) view.loadUrl(failed);
                    });
                });
                v.loadUrl(url);
                MAIN.postDelayed(this::poll, 1500);
            }

            @Override
            public void unavailable(String reason) {
                failure[0] = reason;
                done.countDown();
            }

            void finish() {
                if (prompted && prompter != null) prompter.hide(view);
                TorBrowser.destroy(view);
                view = null;
                done.countDown();
            }

            void poll() {
                if (view == null) return;
                long elapsed = System.currentTimeMillis() - began;
                if (elapsed > (prompted ? 120000 : 45000)) {
                    failure[0] = "timeout";
                    finish();
                    return;
                }
                view.evaluateJavascript(CHECK, state -> {
                    if (view == null) return;
                    String s = state == null ? "" : state.replace("\"", "");
                    if (s.startsWith("C")) {
                        if (s.length() > 1 && s.charAt(1) == 'T' && !prompted && prompter != null) {
                            prompted = true;
                            prompter.show(view, site);
                        }
                        MAIN.postDelayed(this::poll, 1500);
                        return;
                    }
                    if (prompted && prompter != null) {
                        prompter.hide(view);
                        prompted = false;
                    }
                    if (passedAt == 0) passedAt = System.currentTimeMillis();
                    view.evaluateJavascript(Extract.cards(pattern), json -> {
                        if (view == null) return;
                        List<StreamInfoItem> found = parse(json, serviceId);
                        boolean settled = found.size() > 0 && found.size() == lastCount;
                        lastCount = found.size();
                        if (settled || (found.size() > 0 && ++sameCount >= 4)
                                || System.currentTimeMillis() - passedAt > 15000) {
                            out.addAll(found);
                            finish();
                        } else {
                            MAIN.postDelayed(this::poll, 1500);
                        }
                    });
                });
            }
        });
        try {
            done.await(130, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
        Diag.mark("web search " + site + " " + out.size() + " in " + (System.currentTimeMillis() - began) + "ms");
        if (out.isEmpty() && failure[0] != null) throw new IOException("web " + failure[0]);
        return out;
    }

    static List<StreamInfoItem> parse(String value, int serviceId) {
        List<StreamInfoItem> items = new ArrayList<>();
        try {
            String raw = (String) JsonParser.any().from(value);
            JsonArray arr = JsonParser.array().from(raw);
            for (int i = 0; i < arr.size(); i++) {
                JsonObject o = arr.getObject(i);
                String u = o.getString("u", "");
                String t = o.getString("t", "").replaceAll("\\s+", " ").trim();
                if (u.isEmpty() || t.length() < 2) continue;
                StreamInfoItem item = new StreamInfoItem(serviceId, u, t, StreamType.VIDEO_STREAM);
                item.setUploaderName("");
                item.setViewCount(-1);
                item.setDuration(clock(o.getString("d", "")));
                List<Image> thumbs = new ArrayList<>();
                String img = o.getString("i", "");
                if (img.startsWith("http")) thumbs.add(new Image(img, 180, 320, Image.ResolutionLevel.MEDIUM));
                item.setThumbnails(thumbs);
                items.add(item);
            }
        } catch (Exception ignored) {
        }
        return items;
    }

    private static long clock(String text) {
        if (text == null || text.isEmpty()) return -1;
        long total = 0;
        try {
            for (String p : text.split(":")) total = total * 60 + Long.parseLong(p.trim());
            return total;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
