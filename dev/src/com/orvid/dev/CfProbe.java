package com.orvid.dev;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebSettings;
import android.webkit.WebView;

import com.orvid.web.TorBrowser;

import java.util.Collections;
import java.util.concurrent.Executors;

public final class CfProbe extends Activity {

    private static final String CHECK = "(function(){var h=document.documentElement?document.documentElement.innerHTML:'';"
            + "var t=document.title||'';"
            + "var ch=/Just a moment|Attention Required|Bir dakika|Checking/i.test(t)||!!document.querySelector('#challenge-form,#cf-challenge-running,.cf-turnstile,#challenge-stage');"
            + "var ts=!!document.querySelector('iframe[src*=\"challenges.cloudflare.com\"]')||h.indexOf('turnstile')>=0;"
            + "var links=document.querySelectorAll('a[href*=\"/video\"],a[href*=\"/watch\"],a[href*=\"/v/\"]').length;"
            + "var media=(h.match(/\\.(mp4|m3u8)/g)||[]).length;"
            + "return JSON.stringify({challenge:ch,turnstile:ts,links:links,media:media,len:h.length,title:t.substring(0,30)});})()";

    private int stage;
    private static final String DIAG = "(function(){var o={v:[],f:[]};"
            + "[].forEach.call(document.querySelectorAll('video'),function(v){var r=v.getBoundingClientRect();o.v.push(Math.round(r.width)+'x'+Math.round(r.height));});"
            + "[].forEach.call(document.querySelectorAll('iframe'),function(f){var r=f.getBoundingClientRect();var h='';try{h=new URL(f.src,location.href).host;}catch(e){}"
            + "o.f.push(h+' '+Math.round(r.width)+'x'+Math.round(r.height));});"
            + "o.vw=window.innerWidth+'x'+window.innerHeight;o.rs=document.readyState;return JSON.stringify(o);})()";
    private final Handler main = new Handler(Looper.getMainLooper());
    private WebView view;
    private android.view.View cover;
    private String last = "";
    private long started;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        String url = getIntent().getStringExtra("url");
        if (url == null) {
            finish();
            return;
        }
        String host = android.net.Uri.parse(url).getHost();
        started = System.currentTimeMillis();
        TorBrowser.open(this, "cfprobe-" + host, Collections.singleton("*"), Executors.newSingleThreadExecutor(),
                new TorBrowser.Ready() {
                    @Override
                    public void ready(WebView v) {
                        view = v;
                        String def = WebSettings.getDefaultUserAgent(CfProbe.this);
                        java.util.regex.Matcher m = java.util.regex.Pattern.compile("Chrome/(\\d+)").matcher(def);
                        String major = m.find() ? m.group(1) : "130";
                        v.getSettings().setUserAgentString("Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 "
                                + "(KHTML, like Gecko) Chrome/" + major + ".0.0.0 Mobile Safari/537.36");
                        android.widget.FrameLayout root = new android.widget.FrameLayout(CfProbe.this);
                        root.setBackgroundColor(0xFF000000);
                        android.widget.TextView note = new android.widget.TextView(CfProbe.this);
                        note.setText("Test çalışıyor");
                        note.setTextColor(0xFFAAAAAA);
                        note.setGravity(android.view.Gravity.CENTER);
                        v.setAlpha(0f);
                        root.addView(v);
                        root.addView(note, new android.widget.FrameLayout.LayoutParams(-1, -1));
                        cover = note;
                        setContentView(root);
                        v.loadUrl(url);
                        DevUpdate.async("cf " + host + " start");
                        main.postDelayed(CfProbe.this::check, 2000);
                    }

                    @Override
                    public void unavailable(String reason) {
                        DevUpdate.async("cf " + host + " unavailable " + reason);
                        finish();
                    }
                });
    }

    private void check() {
        if (view == null || isFinishing()) return;
        String host = android.net.Uri.parse(getIntent().getStringExtra("url")).getHost();
        view.evaluateJavascript(CHECK, value -> {
            long sec = (System.currentTimeMillis() - started) / 1000;
            boolean box = value != null && value.contains("\\\"turnstile\\\":true");
            view.setAlpha(box ? 1f : 0f);
            cover.setVisibility(box ? android.view.View.GONE : android.view.View.VISIBLE);
            if (value != null && !value.equals(last)) {
                last = value;
                DevUpdate.async("cf " + host + " t=" + sec + "s " + value);
            }
            boolean passed = value != null && value.contains("\\\"challenge\\\":false") && !value.contains("\\\"len\\\":0");
            String pattern = getIntent().getStringExtra("links");
            if (getIntent().getBooleanExtra("focus", false) && pattern != null && passed && sec >= 4 && stage == 0) {
                stage = 1;
                view.evaluateJavascript(com.orvid.web.Extract.cards(pattern), json -> {
                    String first = "";
                    try {
                        String raw = (String) com.grack.nanojson.JsonParser.any().from(json);
                        com.grack.nanojson.JsonArray arr = com.grack.nanojson.JsonParser.array().from(raw);
                        if (!arr.isEmpty()) first = arr.getObject(0).getString("u", "");
                    } catch (Exception ignored) {
                    }
                    DevUpdate.async("cf " + host + " videopage " + (first.isEmpty() ? "none" : "found"));
                    if (!first.isEmpty()) {
                        started = System.currentTimeMillis();
                        last = "";
                        view.loadUrl(first);
                    }
                });
                main.postDelayed(this::check, 3000);
                return;
            }
            if (stage >= 1 && passed && sec >= 3) {
                stage++;
                view.evaluateJavascript(DIAG, diag -> {
                    DevUpdate.async("cf " + host + " DIAG t=" + sec + "s " + diag);
                    view.evaluateJavascript(com.orvid.web.Focus.ON, r -> {
                        DevUpdate.async("cf " + host + " FOCUS " + r);
                        if (stage >= 5 || (r != null && r.contains("focused"))) {
                            DevUpdate.async("cf " + host + " END t=" + sec + "s passed=true");
                            TorBrowser.close(view);
                            view = null;
                            finish();
                        } else {
                            main.postDelayed(this::check, 2000);
                        }
                    });
                });
                return;
            }
            if (pattern != null && passed && sec >= 4 && !getIntent().getBooleanExtra("focus", false)) {
                view.evaluateJavascript(com.orvid.web.Extract.cards(pattern), json -> {
                    int items = 0, titled = 0, thumbs = 0, durs = 0;
                    try {
                        String raw = (String) com.grack.nanojson.JsonParser.any().from(json);
                        com.grack.nanojson.JsonArray arr = com.grack.nanojson.JsonParser.array().from(raw);
                        for (int k = 0; k < arr.size(); k++) {
                            com.grack.nanojson.JsonObject o = arr.getObject(k);
                            items++;
                            if (o.getString("t", "").length() > 2) titled++;
                            if (o.getString("i", "").startsWith("http")) thumbs++;
                            if (!o.getString("d", "").isEmpty()) durs++;
                        }
                    } catch (Exception e) {
                        items = -1;
                    }
                    DevUpdate.async("cf " + host + " EXTRACT items=" + items + " titled=" + titled + " thumbs=" + thumbs + " durations=" + durs);
                    DevUpdate.async("cf " + host + " END t=" + sec + "s passed=true");
                    TorBrowser.close(view);
                    view = null;
                    finish();
                });
                return;
            }
            if (sec >= 45 || (passed && sec >= 6)) {
                DevUpdate.async("cf " + host + " END t=" + sec + "s passed=" + passed);
                TorBrowser.close(view);
                view = null;
                finish();
                return;
            }
            main.postDelayed(this::check, 2000);
        });
    }
}
