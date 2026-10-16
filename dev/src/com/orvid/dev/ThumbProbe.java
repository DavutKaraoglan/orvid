package com.orvid.dev;

import android.app.Activity;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import com.orvid.app.OrvidApp;
import com.orvid.net.Fetch;
import com.orvid.tor.Tor;
import com.orvid.ui.Ui;

import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ThumbProbe extends Activity {

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        String q = getIntent().getStringExtra("q");
        boolean warm = getIntent().getBooleanExtra("warm", true);
        int n = getIntent().getIntExtra("n", 24);
        finish();
        if (q == null) return;
        Handler main = new Handler(Looper.getMainLooper());
        int width = getResources().getDisplayMetrics().widthPixels / 3;
        new Thread(() -> {
            try {
                if (!Tor.get().awaitReady(120000)) {
                    DevUpdate.async("thumb tor not ready");
                    return;
                }
                long t0 = System.currentTimeMillis();
                if (warm) OrvidApp.get().images().warm();
                List<StreamInfoItem> items = com.orvid.yt.Search.first(Fetch.newCircuit("probe"), q, null).items;
                long searched = System.currentTimeMillis() - t0;
                int count = Math.min(n, items.size());
                long[] first = new long[count];
                long[] full = new long[count];
                List<ImageView> views = new ArrayList<>();
                long[] started = new long[1];
                main.post(() -> {
                    started[0] = System.currentTimeMillis();
                    for (int i = 0; i < count; i++) {
                        ImageView v = new ImageView(this);
                        views.add(v);
                        StreamInfoItem it = items.get(i);
                        OrvidApp.get().images().load(v, Ui.thumbnail(it.getThumbnails(), width), width, it.getUrl());
                    }
                    poll(main, views, first, full, started, searched, q, warm, width);
                });
            } catch (Exception e) {
                DevUpdate.async("thumb FAIL " + e);
            }
        }, "thumb-probe").start();
    }

    private void poll(Handler main, List<ImageView> views, long[] first, long[] full, long[] started,
                      long searched, String q, boolean warm, int width) {
        long now = System.currentTimeMillis() - started[0];
        int done = 0;
        for (int i = 0; i < views.size(); i++) {
            if (!(views.get(i).getDrawable() instanceof BitmapDrawable)) continue;
            int w = ((BitmapDrawable) views.get(i).getDrawable()).getBitmap().getWidth();
            if (first[i] == 0) first[i] = now;
            if (full[i] == 0 && w > 160) full[i] = now;
            if (full[i] != 0) done++;
        }
        if (done < views.size() && now < 30000) {
            main.postDelayed(() -> poll(main, views, first, full, started, searched, q, warm, width), 50);
            return;
        }
        DevUpdate.async("thumb q=" + q + " warm=" + warm + " cell=" + width + "px search=" + searched + "ms"
                + " | first image " + stats(first) + " | full " + stats(full));
    }

    private static String stats(long[] t) {
        List<Long> got = new ArrayList<>();
        int missing = 0;
        for (long v : t) if (v > 0) got.add(v); else missing++;
        if (got.isEmpty()) return "none";
        Collections.sort(got);
        return "first " + got.get(0) + "ms half " + got.get(got.size() / 2) + "ms all " + got.get(got.size() - 1)
                + "ms" + (missing > 0 ? " missing " + missing : "");
    }
}
