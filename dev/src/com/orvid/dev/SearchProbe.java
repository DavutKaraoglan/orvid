package com.orvid.dev;

import android.app.Activity;
import android.os.Bundle;

import com.orvid.net.Fetch;
import com.orvid.net.Hedge;
import com.orvid.search.Filters;
import com.orvid.search.Query;
import com.orvid.search.Source;
import com.orvid.ui.Sources;
import com.orvid.yt.Visionos;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class SearchProbe extends Activity {

    private static final ExecutorService PARALLEL = Executors.newCachedThreadPool();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        String text = getIntent().getStringExtra("q");
        boolean adult = getIntent().getBooleanExtra("adult", true);
        finish();
        if (text == null) return;
        Source.adultUnlocked = adult;
        Filters f = new Filters();
        for (Source s : Sources.current()) f.set(s, true);
        Query q = Query.parse(text);
        long t0 = System.currentTimeMillis();
        AtomicLong first = new AtomicLong();
        AtomicInteger pending = new AtomicInteger();
        Runnable done = () -> {
            if (pending.decrementAndGet() == 0) {
                DevUpdate.async("search ALL " + (System.currentTimeMillis() - t0) + "ms first=" + first.get() + "ms");
            }
        };
        java.util.function.BiConsumer<String, Integer> report = (name, n) -> {
            long ms = System.currentTimeMillis() - t0;
            if (n > 0) first.compareAndSet(0, ms);
            DevUpdate.async("search " + name + " " + ms + "ms " + n);
            done.run();
        };
        pending.incrementAndGet();
        PARALLEL.execute(() -> {
            try {
                Hedge.Result<com.orvid.yt.Search.Page> r = Hedge.call(Visionos.takeCircuit(), Visionos::takeCircuit,
                        Fetch.torMode() ? 1500 : 0, c -> com.orvid.yt.Search.first(c, q.platformQuery, f.youtubeParams()));
                report.accept("youtube", r.value.items.size());
            } catch (Exception e) {
                report.accept("youtube FAIL", 0);
            }
        });
        for (Source src : Sources.current()) {
            if (!src.wanted(f, q)) continue;
            pending.incrementAndGet();
            PARALLEL.execute(() -> {
                try {
                    Hedge.Result<Source.Page> r = Hedge.call(Visionos.takeCircuit(), Visionos::takeCircuit, 2500,
                            c -> src.search(c, q.platformQuery, f, null));
                    report.accept(src.key, r.value.items.size());
                } catch (Exception e) {
                    report.accept(src.key + " FAIL", 0);
                }
            });
        }
        DevUpdate.async("search started " + pending.get() + " sources");
    }
}
