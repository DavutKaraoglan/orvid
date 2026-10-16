package com.orvid.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;

import com.orvid.R;
import com.orvid.app.CrashLog;
import com.orvid.app.Job;
import com.orvid.app.OrvidApp;
import com.orvid.net.Diag;
import com.orvid.net.Fetch;
import com.orvid.tor.Tor;
import com.orvid.dm.Dailymotion;
import com.orvid.net.Hedge;
import com.orvid.search.Filters;
import com.orvid.search.Query;
import com.orvid.search.Source;
import com.orvid.search.Ranker;
import com.orvid.yt.Search;
import com.orvid.yt.Visionos;

import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MainActivity extends Activity implements Tor.Listener {

    private TextView title;
    private EditText field;
    private ImageButton searchButton;
    private ImageButton layoutButton;
    private TextView torBanner;
    private ListView list;
    private View progress;
    private TextView message;
    private VideoAdapter adapter;
    private boolean searching;

    private boolean searched;
    private int searchGeneration;
    private String circuit;
    private String continuation;
    private Query query;
    private boolean dmMore;
    private int dmPage;
    private String vkNext;
    private String vkCircuit;
    private TextView correction;
    private Filters filters;
    private String lastInput;
    private String correctedQuery;
    private static final java.util.concurrent.ExecutorService PARALLEL =
            java.util.concurrent.Executors.newCachedThreadPool();
    private Job<?> running;
    private boolean loadingMore;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        if (android.os.Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(false);
        try {
            startService(new android.content.Intent(this, com.orvid.app.Reaper.class));
        } catch (Exception ignored) {
        }
        pinch = new android.view.ScaleGestureDetector(this, new android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
            private float start;

            @Override
            public boolean onScaleBegin(android.view.ScaleGestureDetector d) {
                start = d.getCurrentSpan();
                return true;
            }

            @Override
            public void onScaleEnd(android.view.ScaleGestureDetector d) {
                if (start <= 0) return;
                float ratio = d.getCurrentSpan() / start;
                if (ratio > 1.6f && !com.orvid.search.Source.adultUnlocked) setAdult(true);
                else if (ratio < 0.6f && com.orvid.search.Source.adultUnlocked) setAdult(false);
            }
        });
        title = findViewById(R.id.title);
        field = findViewById(R.id.search_field);
        searchBox = findViewById(R.id.search_box);
        clearButton = findViewById(R.id.search_clear);
        clearButton.setOnClickListener(v -> {
            field.setText("");
            field.requestFocus();
            InputMethodManager imm = getSystemService(InputMethodManager.class);
            if (imm != null) imm.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT);
        });
        searchButton = findViewById(R.id.btn_search);
        torBanner = findViewById(R.id.tor_banner);
        torBanner.setOnClickListener(v -> {
            if (Tor.get().state() == Tor.State.FAILED) OrvidApp.get().restartTor();
        });
        list = findViewById(R.id.list);
        progress = findViewById(R.id.progress);
        message = findViewById(R.id.message);
        correction = findViewById(R.id.correction);
        correction.setOnClickListener(v -> {
            if (correctedQuery == null) return;
            String fixed = correctedQuery;
            field.setText(fixed);
            runSearch(fixed);
        });

        filters = Filters.load(getSharedPreferences("settings", MODE_PRIVATE));
        com.orvid.yt.Related.safeSearch = filters.hideAdult;
        adapter = new VideoAdapter(this, OrvidApp.get().layoutMode(), item -> {
            if (com.orvid.web.WebSites.forUrl(item.getUrl()) != null || com.orvid.adult.Tubes.webOnly(item.getUrl())) {
                startActivity(new Intent(this, WebPlayerActivity.class).putExtra(WebPlayerActivity.EXTRA_URL, item.getUrl()));
                return;
            }
            openingPlayer = true;
            startActivity(new Intent(this, PlayerActivity.class)
                        .putExtra(PlayerActivity.EXTRA_URL, item.getUrl())
                        .putExtra(PlayerActivity.EXTRA_SERVICE, item.getServiceId())
                        .putExtra(PlayerActivity.EXTRA_THUMB, Ui.thumbnail(item.getThumbnails(), 640)));
            overridePendingTransition(0, 0);
        });
        previewer = new Previewer(this);
        com.orvid.web.WebSearch.prompter = new com.orvid.web.WebSearch.Prompter() {
            private android.app.Dialog dialog;

            @Override
            public void show(android.webkit.WebView view, String site) {
                if (isFinishing() || dialog != null) return;
                dialog = new android.app.Dialog(MainActivity.this);
                dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
                android.widget.LinearLayout box = new android.widget.LinearLayout(MainActivity.this);
                box.setOrientation(android.widget.LinearLayout.VERTICAL);
                box.setBackgroundColor(getColor(R.color.elevated));
                TextView label = new TextView(MainActivity.this);
                label.setText(getString(R.string.web_verify, site));
                label.setTextColor(getColor(R.color.text_primary));
                int p = Ui.dp(MainActivity.this, 14);
                label.setPadding(p, p, p, p);
                box.addView(label);
                if (view.getParent() != null) ((android.view.ViewGroup) view.getParent()).removeView(view);
                box.addView(view, new android.widget.LinearLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(MainActivity.this, 320)));
                dialog.setContentView(box);
                dialog.setCancelable(true);
                dialog.show();
            }

            @Override
            public void hide(android.webkit.WebView view) {
                if (view != null && view.getParent() != null) ((android.view.ViewGroup) view.getParent()).removeView(view);
                if (dialog != null) dialog.dismiss();
                dialog = null;
            }
        };
        adapter.setPreviewer(previewer);
        list.setAdapter(adapter);
        layoutButton = findViewById(R.id.btn_layout);
        layoutButton.setImageResource(R.drawable.ic_filter);
        layoutButton.setOnClickListener(v -> FilterSheet.show(this, OrvidApp.get().layoutMode(), filters,
                new FilterSheet.Listener() {
                    @Override
                    public void onLayout(int mode) {
                        int current = OrvidApp.get().layoutMode();
                        OrvidApp.get().setLayoutMode(mode);
                        int first = list.getFirstVisiblePosition();
                        int firstItem = first * VideoAdapter.columns(current);
                        adapter.setMode(mode);
                        list.setSelection(firstItem / VideoAdapter.columns(mode));
                    }

                    @Override
                    public void onFilters(Filters f) {
                        f.save(getSharedPreferences("settings", MODE_PRIVATE));
                        com.orvid.yt.Related.safeSearch = f.hideAdult;
                        if (searched && lastInput != null) runSearch(lastInput);
                    }
                }));
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                adapter.setIdle(scrollState == SCROLL_STATE_IDLE);
            }

            @Override
            public void onScroll(AbsListView view, int first, int visible, int total) {
                if (total > 0 && first + visible >= total - 3) loadMore();
            }
        });

        searchButton.setOnClickListener(v -> {
            if (!searching) {
                openSearch();
                return;
            }
            dismissSuggest();
            String q = field.getText().toString().trim();
            if (!q.isEmpty()) {
                hideKeyboard();
                runSearch(q);
            }
        });
        field.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                clearButton.setVisibility(s.length() == 0 ? View.GONE : View.VISIBLE);
                if (s.length() > 0) OrvidApp.get().images().warm();
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                scheduleSuggest(s.toString().trim());
            }
        });
        field.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_SEARCH) return false;
            dismissSuggest();
            String q = field.getText().toString().trim();
            if (!q.isEmpty()) {
                hideKeyboard();
                runSearch(q);
            }
            return true;
        });

        message.setText(R.string.tagline);
        mini = findViewById(R.id.mini);
        miniVideo = findViewById(R.id.mini_video);
        miniPlay = findViewById(R.id.mini_play);
        mini.setOnClickListener(v -> {
            if (!com.orvid.play.Session.active()) return;
            openingPlayer = true;
            startActivity(new Intent(this, PlayerActivity.class)
                    .putExtra(PlayerActivity.EXTRA_URL, com.orvid.play.Session.url()));
            overridePendingTransition(0, 0);
        });
        miniPlay.setOnClickListener(v -> {
            androidx.media3.exoplayer.ExoPlayer p = com.orvid.play.Session.player();
            if (p == null) return;
            if (p.isPlaying()) p.pause();
            else p.play();
        });
        findViewById(R.id.mini_close).setOnClickListener(v -> {
            detachMini();
            com.orvid.play.Session.release();
            mini.setVisibility(View.GONE);
        });
        CrashLog.showPending(this);
        showHome();
    }

    private View mini;
    private android.view.TextureView miniVideo;
    private ImageButton miniPlay;
    private boolean openingPlayer;
    private androidx.media3.common.Player attached;
    private final androidx.media3.common.Player.Listener miniListener = new androidx.media3.common.Player.Listener() {
        @Override
        public void onIsPlayingChanged(boolean playing) {
            miniPlay.setImageResource(playing ? R.drawable.ic_pause_white : R.drawable.ic_play_white);
        }
    };

    private void attachMini() {
        androidx.media3.exoplayer.ExoPlayer p = com.orvid.play.Session.player();
        if (p == null) {
            mini.setVisibility(View.GONE);
            return;
        }
        mini.setVisibility(View.VISIBLE);
        ((TextView) findViewById(R.id.mini_title)).setText(com.orvid.play.Session.title);
        ((TextView) findViewById(R.id.mini_author)).setText(com.orvid.play.Session.author);
        p.setVideoTextureView(miniVideo);
        p.addListener(miniListener);
        attached = p;
        miniListener.onIsPlayingChanged(p.isPlaying());
    }

    private void detachMini() {
        if (attached == null) return;
        attached.clearVideoTextureView(miniVideo);
        attached.removeListener(miniListener);
        attached = null;
    }

    private void showHome() {
        adapter.set(Collections.<StreamInfoItem>emptyList());
        showMessage(getString(R.string.tagline));
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (previewer != null) previewer.stop();
        detachMini();
    }

    @Override
    protected void onResume() {
        super.onResume();
        openingPlayer = false;
        attachMini();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Tor.get().addListener(this);
        refreshTor();
        com.orvid.app.UpdateCheck.run(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!openingPlayer && com.orvid.play.Session.active() && !isChangingConfigurations()) {
            com.orvid.play.Session.player().pause();
        }
        Tor.get().removeListener(this);
    }

    @Override
    public void onTorChanged() {
        runOnUiThread(this::refreshTor);
    }

    private void refreshTor() {
        Tor.State st = Tor.get().state();
        if (st == Tor.State.STARTING) {
            com.orvid.tor.Bridges.Mode mode = Tor.get().mode();
            torBanner.setText(mode == com.orvid.tor.Bridges.Mode.DIRECT
                    ? getString(R.string.tor_banner_starting, Tor.get().progress())
                    : getString(R.string.tor_banner_bridge, mode.name().toLowerCase(java.util.Locale.ROOT), Tor.get().progress()));
            torBanner.setVisibility(View.VISIBLE);
        } else if (st == Tor.State.FAILED) {
            torBanner.setText(OrvidApp.get().bridges() ? getString(R.string.tor_banner_failed)
                    : getString(R.string.tor_banner_failed) + " " + getString(R.string.tor_bridge_hint));
            torBanner.setVisibility(View.VISIBLE);
        } else {
            torBanner.setVisibility(View.GONE);
        }
    }

    private View searchBox;
    private View clearButton;

    private void openSearch() {
        searching = true;
        title.setVisibility(View.GONE);
        searchBox.setVisibility(View.VISIBLE);
        searchButton.setVisibility(View.GONE);
        field.requestFocus();
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (imm != null) imm.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT);
    }

    private void closeSearch() {
        searching = false;
        hideKeyboard();
        field.setText("");
        searchBox.setVisibility(View.GONE);
        searchButton.setVisibility(View.VISIBLE);
        title.setVisibility(View.VISIBLE);
    }

    private void hideKeyboard() {
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (imm != null) imm.hideSoftInputFromWindow(field.getWindowToken(), 0);
        field.clearFocus();
    }

    private static final double[] WEIGHTS = weights();
    private static final boolean[] TAGGED = tagged();

    private static boolean[] tagged() {
        boolean[] t = new boolean[CORE + Sources.EXTRA.size()];
        for (int i = 0; i < Sources.EXTRA.size(); i++) t[CORE + i] = Sources.EXTRA.get(i).adult;
        return t;
    }
    private static final long HEDGE_MS = 1500;

    private static final class Batch {
        final List<StreamInfoItem> youtube = new ArrayList<>();
        final List<StreamInfoItem> dailymotion = new ArrayList<>();
        final List<StreamInfoItem> vk = new ArrayList<>();
        final List<List<StreamInfoItem>> extra = new ArrayList<>();
        final String[] extraNext = new String[Sources.EXTRA.size()];
        String vkNext;
        String ytContinuation;
        boolean dmMore;
    }

    private int pendingSources;
    private Exception lastSourceError;

    private void runSearch(final String input) {
        OrvidApp.get().images().warm();
        dismissSuggest();
        if (running != null) running.cancel();
        running = null;
        adapter.set(Collections.<StreamInfoItem>emptyList());
        message.setVisibility(View.GONE);
        correction.setVisibility(View.GONE);
        adapter.setSkeleton(8);
        continuation = null;
        circuit = null;
        dmMore = false;
        dmPage = 1;
        vkNext = null;
        vkCircuit = null;
        java.util.Arrays.fill(extraNext, null);
        java.util.Arrays.fill(extraCircuit, null);
        loadingMore = false;
        correctedQuery = null;
        lastSourceError = null;
        if (flush != null) list.removeCallbacks(flush);
        flush = null;
        if (rankTask != null) list.removeCallbacks(rankTask);
        rankTask = null;
        rankDirty = false;
        shown = false;
        arrived = null;
        final Query q = Query.parse(input);
        final Filters f = filters;
        query = q;
        lastInput = input;
        adapter.setQuery(q);
        searched = true;
        final int generation = ++searchGeneration;
        pendingSources = 0;

        if (searchScope != null) searchScope.cancel();
        searchScope = new Fetch.Scope();
        extraQuery = q;
        java.util.Arrays.fill(extraStarted, null);
        List<Queued> jobs = new ArrayList<>();

        if (!q.site.equals(Query.SITE_DAILYMOTION) && f.youtube) {
            pendingSources++;
            jobs.add(queued(0, 0, () -> {
                try {
                    Hedge.Result<Search.Page> r = Hedge.call(Visionos.takeCircuit(), Visionos::takeCircuit,
                            Fetch.torMode() ? HEDGE_MS : 0, c -> Search.first(c, q.platformQuery, f.youtubeParams()));
                    runOnUiThread(() -> {
                        if (generation != searchGeneration) return;
                        circuit = r.circuit;
                        continuation = r.value.continuation;
                        correctedQuery = r.value.corrected;
                        if (correctedQuery != null) {
                            correction.setText(getString(R.string.showing_results_for, correctedQuery));
                            correction.setVisibility(View.VISIBLE);
                        }
                        deliver(generation, q, f, 0, r.value.items);
                        if (correctedQuery != null && !Query.fold(correctedQuery).equals(Query.fold(q.platformQuery))) {
                            secondWave(generation, Query.parse(correctedQuery), f);
                        }
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> sourceFailed(generation, e));
                }
            }));
        }
        if (!q.site.equals(Query.SITE_YOUTUBE) && f.dailymotion) {
            pendingSources++;
            jobs.add(queued(1, 0, () -> {
                try {
                    Dailymotion.Page page = Dailymotion.search(Visionos.takeCircuit(), q.platformQuery, 1,
                            f.dailymotionParams(), f.type == Filters.TYPE_SHORTS);
                    runOnUiThread(() -> {
                        if (generation != searchGeneration) return;
                        dmMore = page.hasMore;
                        deliver(generation, q, f, 1, page.items);
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> sourceFailed(generation, e));
                }
            }));
        }
        if (q.site.isEmpty() && f.vk && f.type != Filters.TYPE_SHORTS) {
            pendingSources++;
            jobs.add(queued(Query.CYRILLIC.equals(q.script()) ? 2 : 4, -0.8, () -> {
                try {
                    Hedge.Result<com.orvid.vk.Vk.Page> r = Hedge.race(com.orvid.vk.Vk.circuits(Visionos::takeCircuit),
                            1200, c -> com.orvid.vk.Vk.search(c, q.platformQuery, null));
                    runOnUiThread(() -> {
                        if (generation != searchGeneration) return;
                        vkNext = r.value.next;
                        vkCircuit = r.circuit;
                        deliver(generation, q, f, 2, r.value.items);
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> sourceFailed(generation, e));
                }
            }));
        }
        for (int i = 0; i < Sources.EXTRA.size(); i++) {
            final Source src = Sources.EXTRA.get(i);
            final int slot = i;
            if (!src.wanted(f, q)) continue;
            pendingSources++;
            jobs.add(queued(src.priority(q), -src.weight, () -> runExtra(generation, f, src, slot, null)));
        }
        if (pendingSources == 0) {
            adapter.set(Collections.<StreamInfoItem>emptyList());
            showMessage(getString(R.string.no_results));
        }
        java.util.Collections.sort(jobs);
        for (Queued job : jobs) SEARCH.execute(job);
    }

    private void runExtra(int generation, Filters f, Source src, int slot, Query forced) {
        Query use;
        synchronized (extraStarted) {
            use = forced != null ? forced : extraQuery;
            extraStarted[slot] = use;
        }
        if (!src.wanted(f, use)) {
            runOnUiThread(() -> sourceFailed(generation, null));
            return;
        }
        try {
            Hedge.Result<Source.Page> first = Hedge.call(Fetch.newCircuit("x"), () -> Fetch.newCircuit("x"),
                    VK_HEDGE_MS, c -> src.search(c, use.platformQuery, f, null));
            if (first.value.items.isEmpty()) {
                Diag.mark(src.key + " empty, asking again from another exit");
                Hedge.Result<Source.Page> again = Hedge.call(Fetch.newCircuit("x"), () -> Fetch.newCircuit("x"),
                        VK_HEDGE_MS, c -> src.search(c, use.platformQuery, f, null));
                if (!again.value.items.isEmpty()) first = again;
            }
            final Hedge.Result<Source.Page> r = first;
            runOnUiThread(() -> {
                if (generation != searchGeneration) return;
                extraNext[slot] = r.value.next;
                extraCircuit[slot] = r.circuit;
                deliver(generation, use, f, CORE + slot, r.value.items);
            });
        } catch (Exception e) {
            runOnUiThread(() -> sourceFailed(generation, e));
        }
    }

    private void secondWave(int generation, Query q2, Filters f) {
        query = q2;
        adapter.setQuery(q2);
        List<Queued> jobs = new ArrayList<>();
        synchronized (extraStarted) {
            extraQuery = q2;
            for (int i = 0; i < Sources.EXTRA.size(); i++) {
                final Source src = Sources.EXTRA.get(i);
                final int slot = i;
                if (extraStarted[i] == null || extraStarted[i] == q2 || !src.wanted(f, q2)) continue;
                pendingSources++;
                jobs.add(queued(src.priority(q2), -src.weight, () -> runExtra(generation, f, src, slot, q2)));
            }
        }
        java.util.Collections.sort(jobs);
        for (Queued job : jobs) SEARCH.execute(job);
    }

    private Fetch.Scope searchScope;
    private volatile Query extraQuery;
    private final Query[] extraStarted = new Query[Sources.EXTRA.size()];
    private static final int SEARCH_SLOTS = 16;
    private static final java.util.concurrent.ThreadPoolExecutor SEARCH = new java.util.concurrent.ThreadPoolExecutor(
            SEARCH_SLOTS, SEARCH_SLOTS, 30, java.util.concurrent.TimeUnit.SECONDS,
            new java.util.concurrent.PriorityBlockingQueue<Runnable>());
    private static final java.util.concurrent.atomic.AtomicLong ORDER = new java.util.concurrent.atomic.AtomicLong();

    static {
        SEARCH.allowCoreThreadTimeOut(true);
    }

    private static final class Queued implements Runnable, Comparable<Queued> {
        final int priority;
        final double tie;
        final long order = ORDER.incrementAndGet();
        final Fetch.Scope scope;
        final Runnable body;

        Queued(int priority, double tie, Fetch.Scope scope, Runnable body) {
            this.priority = priority;
            this.tie = tie;
            this.scope = scope;
            this.body = body;
        }

        @Override
        public void run() {
            if (scope.cancelled()) return;
            try {
                Fetch.within(scope, () -> {
                    body.run();
                    return null;
                });
            } catch (Exception ignored) {
            }
        }

        @Override
        public int compareTo(Queued o) {
            if (priority != o.priority) return Integer.compare(priority, o.priority);
            if (tie != o.tie) return Double.compare(tie, o.tie);
            return Long.compare(order, o.order);
        }
    }

    private Queued queued(int priority, double tie, Runnable body) {
        return new Queued(priority, tie, searchScope, body);
    }

    private static final int CORE = 3;
    private final String[] extraNext = new String[Sources.EXTRA.size()];
    private final String[] extraCircuit = new String[Sources.EXTRA.size()];

    private static double[] weights() {
        double[] w = new double[CORE + Sources.EXTRA.size()];
        w[0] = 1.0;
        w[1] = 0.7;
        w[2] = 0.8;
        for (int i = 0; i < Sources.EXTRA.size(); i++) w[CORE + i] = Sources.EXTRA.get(i).weight;
        return w;
    }

    private static final long VK_HEDGE_MS = 2500;

    private boolean anyExtraNext() {
        for (String n : extraNext) if (n != null) return true;
        return false;
    }

    private static final long GRACE_MS = 600;
    private List<List<StreamInfoItem>> arrived;
    private boolean shown;
    private Runnable flush;

    private void deliver(int generation, Query q, Filters f, int source, List<StreamInfoItem> items) {
        if (generation != searchGeneration) return;
        pendingSources--;
        if (arrived == null) {
            arrived = new ArrayList<>();
            for (int i = 0; i < WEIGHTS.length; i++) arrived.add(new ArrayList<StreamInfoItem>());
        }
        List<StreamInfoItem> merged = new ArrayList<>(arrived.get(source));
        java.util.Set<String> have = new java.util.HashSet<>();
        for (StreamInfoItem i : merged) have.add(i.getUrl());
        for (StreamInfoItem i : items) if (have.add(i.getUrl())) merged.add(i);
        arrived.set(source, merged);
        if (!shown) {
            if (pendingSources <= 0 || (source == 0 && !items.isEmpty())) {
                if (flush != null) list.removeCallbacks(flush);
                flush = null;
                requestRank(generation, true);
            } else if (flush == null) {
                flush = () -> requestRank(generation, true);
                list.postDelayed(flush, GRACE_MS);
            }
        } else {
            requestRank(generation, false);
        }
        finishIfDone();
    }

    private static final long RANK_THROTTLE_MS = 300;
    private static final java.util.concurrent.ExecutorService RANK = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "orvid-rank");
        t.setDaemon(true);
        return t;
    });
    private boolean rankBusy;
    private boolean rankDirty;
    private Runnable rankTask;

    private void requestRank(int generation, boolean immediate) {
        if (rankBusy) {
            rankDirty = true;
            return;
        }
        if (rankTask != null) {
            if (!immediate) return;
            list.removeCallbacks(rankTask);
        }
        rankTask = () -> {
            rankTask = null;
            startRank(generation);
        };
        list.postDelayed(rankTask, immediate ? 0 : RANK_THROTTLE_MS);
    }

    private void startRank(int generation) {
        if (generation != searchGeneration || arrived == null || query == null) return;
        rankBusy = true;
        rankDirty = false;
        List<List<StreamInfoItem>> snapshot = new ArrayList<>(arrived);
        Query use = query;
        Filters f = filters;
        RANK.execute(() -> {
            List<StreamInfoItem> ranked;
            try {
                ranked = f.arrange(use, snapshot, WEIGHTS, TAGGED);
            } catch (RuntimeException e) {
                ranked = null;
            }
            List<StreamInfoItem> result = ranked;
            runOnUiThread(() -> {
                rankBusy = false;
                if (generation == searchGeneration && result != null) applyRanked(result);
                if (rankDirty) {
                    rankDirty = false;
                    requestRank(searchGeneration, !shown);
                }
                finishIfDone();
            });
        });
    }

    private void applyRanked(List<StreamInfoItem> ranked) {
        if (shown) {
            mergeTail(ranked);
            return;
        }
        if (ranked.isEmpty() && pendingSources > 0) return;
        shown = true;
        adapter.set(ranked);
        list.setSelection(0);
        prepareTop(ranked);
    }


    private static final int PREPARE_TOP = 3;

    private static void prepareTop(List<StreamInfoItem> ranked) {
        int started = 0;
        for (int i = 0; i < Math.min(ranked.size(), 8) && started < PREPARE_TOP; i++) {
            String url = ranked.get(i).getUrl();
            if (!com.orvid.play.Resolve.preparable(url)) continue;
            com.orvid.play.Resolve.prepare(url);
            started++;
        }
    }

    private void mergeTail(List<StreamInfoItem> ranked) {
        List<StreamInfoItem> current = adapter.items();
        int visible = Math.max(0, list.getLastVisiblePosition() - list.getHeaderViewsCount() + 1);
        int keep = Math.min(current.size(), visible * adapter.columns() + 4);
        List<StreamInfoItem> merged = new ArrayList<>(current.subList(0, keep));
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (StreamInfoItem i : merged) seen.add(i.getUrl());
        for (StreamInfoItem i : ranked) if (seen.add(i.getUrl())) merged.add(i);
        for (StreamInfoItem i : current.subList(keep, current.size())) if (seen.add(i.getUrl())) merged.add(i);
        adapter.set(merged);
    }

    private void sourceFailed(int generation, Exception e) {
        if (generation != searchGeneration) return;
        pendingSources--;
        lastSourceError = e;
        finishIfDone();
    }

    private void finishIfDone() {
        if (pendingSources <= 0 && !shown && arrived != null && query != null && !rankBusy) {
            if (flush != null) list.removeCallbacks(flush);
            flush = null;
            requestRank(searchGeneration, true);
            return;
        }
        if (pendingSources > 0 || adapter.itemCount() > 0 || rankBusy || rankTask != null) return;
        adapter.set(Collections.<StreamInfoItem>emptyList());
        showMessage(lastSourceError != null ? Ui.error(this, lastSourceError) : emptyMessage());
    }

    private String emptyMessage() {
        List<String> active = new ArrayList<>();
        if (filters.date != Filters.DATE_ANY) active.add(getString(R.string.filter_date));
        if (filters.orientation != Filters.ORIENT_ANY) active.add(getString(R.string.filter_orientation));
        if (filters.duration != Filters.DURATION_ANY) active.add(getString(R.string.filter_duration));
        if (filters.type != Filters.TYPE_ALL) active.add(getString(R.string.filter_type));
        return active.isEmpty() ? getString(R.string.no_results)
                : getString(R.string.no_results_filters, android.text.TextUtils.join(", ", active));
    }

    private void loadMore() {
        if (loadingMore || !searched || query == null) return;
        if (continuation == null && !dmMore && vkNext == null && !anyExtraNext()) return;
        loadingMore = true;
        final int generation = searchGeneration;
        final String ownerCircuit = circuit;
        final String token = continuation;
        final boolean wantDm = dmMore;
        final int nextDm = dmPage + 1;
        final String wantVk = vkNext;
        final String ownerVk = vkCircuit;
        final String[] wantExtra = extraNext.clone();
        final String[] ownerExtra = extraCircuit.clone();
        final Query q = query;
        final Filters f = filters;
        final Fetch.Scope scope = searchScope;
        new Job<Batch>(this) {
            @Override
            protected Batch load() throws Exception {
                return Fetch.within(scope, this::collect);
            }

            private Batch collect() throws Exception {
                Batch batch = new Batch();
                java.util.concurrent.Future<Dailymotion.Page> dm = !wantDm ? null
                        : PARALLEL.submit(inScope(scope, () -> Dailymotion.search(Visionos.takeCircuit(), q.platformQuery, nextDm, f.dailymotionParams(), f.type == Filters.TYPE_SHORTS)));
                java.util.concurrent.Future<com.orvid.vk.Vk.Page> vkf = wantVk == null || ownerVk == null ? null
                        : PARALLEL.submit(inScope(scope, () -> Fetch.retrying(ownerVk, c -> com.orvid.vk.Vk.search(c, q.platformQuery, wantVk))));
                if (token != null) {
                    Search.Page p = Fetch.retrying(ownerCircuit, c -> Search.more(c, token));
                    batch.youtube.addAll(p.items);
                    batch.ytContinuation = p.continuation;
                }
                if (dm != null) {
                    try {
                        Dailymotion.Page page = dm.get();
                        batch.dailymotion.addAll(page.items);
                        batch.dmMore = page.hasMore;
                    } catch (Exception ignored) {
                    }
                }
                if (vkf != null) {
                    try {
                        com.orvid.vk.Vk.Page page = vkf.get();
                        batch.vk.addAll(page.items);
                        batch.vkNext = page.next;
                    } catch (Exception ignored) {
                    }
                }
                List<java.util.concurrent.Future<Source.Page>> extraJobs = new ArrayList<>();
                for (int i = 0; i < Sources.EXTRA.size(); i++) {
                    final Source src = Sources.EXTRA.get(i);
                    final String want = wantExtra[i];
                    final String owner = ownerExtra[i];
                    extraJobs.add(want == null || owner == null ? null : PARALLEL.submit(
                            inScope(scope, () -> Fetch.retrying(owner, c -> src.search(c, q.platformQuery, f, want)))));
                }
                for (int i = 0; i < extraJobs.size(); i++) {
                    List<StreamInfoItem> got = new ArrayList<>();
                    if (extraJobs.get(i) != null) {
                        try {
                            Source.Page page = extraJobs.get(i).get();
                            got.addAll(page.items);
                            batch.extraNext[i] = page.next;
                        } catch (Exception ignored) {
                        }
                    }
                    batch.extra.add(got);
                }
                return batch;
            }

            @Override
            protected void show(Batch result) {
                if (generation != searchGeneration) return;
                continuation = result.ytContinuation;
                dmMore = result.dmMore;
                if (wantDm) dmPage = nextDm;
                if (wantVk != null) vkNext = result.vkNext;
                for (int i = 0; i < wantExtra.length; i++) if (wantExtra[i] != null) extraNext[i] = result.extraNext[i];
                List<List<StreamInfoItem>> lists = new ArrayList<>(java.util.Arrays.asList(result.youtube, result.dailymotion, result.vk));
                lists.addAll(result.extra);
                adapter.add(f.arrange(q, lists, WEIGHTS, TAGGED));
                loadingMore = false;
            }

            @Override
            protected void fail(Exception error) {
                loadingMore = false;
            }
        }.start();
    }

    private static <T> java.util.concurrent.Callable<T> inScope(Fetch.Scope scope, java.util.concurrent.Callable<T> work) {
        return () -> Fetch.within(scope, work);
    }

    private void showMessage(String text) {
        message.setText(text);
        message.setVisibility(View.VISIBLE);
    }

    private boolean onHome() {
        return !searched && running == null;
    }

    private void goHome() {
        if (searchScope != null) searchScope.cancel();
        if (running != null) running.cancel();
        running = null;
        searched = false;
        searchGeneration++;
        continuation = null;
        query = null;
        dmMore = false;
        adapter.setQuery(null);
        correction.setVisibility(View.GONE);
        loadingMore = false;
        adapter.set(Collections.<StreamInfoItem>emptyList());
        progress.setVisibility(View.GONE);
        closeSearch();
        showHome();
    }

    @Override
    public void onBackPressed() {
        if (searching || !onHome()) {
            goHome();
            return;
        }
        finishAndRemoveTask();
        com.orvid.app.OrvidApp.get().wipeAndExit();
    }

    private android.view.ScaleGestureDetector pinch;
    private final Panic panic = new Panic();
    private android.widget.ListPopupWindow suggestPopup;
    private int suggestVersion;
    private Runnable suggestJob;
    private boolean suppressSuggest;

    private void scheduleSuggest(String text) {
        if (suggestJob != null) field.removeCallbacks(suggestJob);
        final int version = ++suggestVersion;
        if (suppressSuggest || text.length() < 2 || !field.hasFocus() || com.orvid.search.Source.adultUnlocked) {
            dismissSuggest();
            return;
        }
        suggestJob = () -> PARALLEL.execute(() -> {
            try {
                List<String> found = com.orvid.yt.Suggest.fetch(text);
                runOnUiThread(() -> {
                    if (version == suggestVersion && field.hasFocus()) showSuggest(found);
                });
            } catch (Exception ignored) {
            }
        });
        field.postDelayed(suggestJob, 250);
    }

    private void showSuggest(List<String> items) {
        if (items.isEmpty()) {
            dismissSuggest();
            return;
        }
        if (suggestPopup == null) {
            suggestPopup = new android.widget.ListPopupWindow(this);
            suggestPopup.setAnchorView(field);
            suggestPopup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(getColor(R.color.elevated)));
            suggestPopup.setInputMethodMode(android.widget.PopupWindow.INPUT_METHOD_NEEDED);
            suggestPopup.setModal(false);
        }
        suggestPopup.setAdapter(new android.widget.ArrayAdapter<String>(this, 0, items) {
            @Override
            public View getView(int position, View convert, android.view.ViewGroup parent) {
                TextView t = convert instanceof TextView ? (TextView) convert : new TextView(MainActivity.this);
                t.setText(getItem(position));
                t.setTextColor(getColor(R.color.text_primary));
                t.setTextSize(16);
                int p = Ui.dp(MainActivity.this, 14);
                t.setPadding(p, p, p, p);
                t.setSingleLine(true);
                t.setEllipsize(android.text.TextUtils.TruncateAt.END);
                return t;
            }
        });
        suggestPopup.setOnItemClickListener((parent, view, position, id) -> {
            String pick = items.get(position);
            dismissSuggest();
            suppressSuggest = true;
            field.setText(pick);
            field.setSelection(pick.length());
            suppressSuggest = false;
            hideKeyboard();
            runSearch(pick);
        });
        suggestPopup.setWidth(Math.max(field.getWidth(), Ui.dp(this, 240)));
        if (!suggestPopup.isShowing()) suggestPopup.show();
        else suggestPopup.show();
    }

    private void dismissSuggest() {
        suggestVersion++;
        if (suggestJob != null) field.removeCallbacks(suggestJob);
        if (suggestPopup != null && suggestPopup.isShowing()) suggestPopup.dismiss();
    }
    private Previewer previewer;

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent e) {
        if (panic.feed(this, e)) return true;
        if (pinch != null) pinch.onTouchEvent(e);
        return super.dispatchTouchEvent(e);
    }

    private void setAdult(boolean on) {
        com.orvid.search.Source.adultUnlocked = on;
        getWindow().getDecorView().performHapticFeedback(android.os.Build.VERSION.SDK_INT >= 30
                ? android.view.HapticFeedbackConstants.CONFIRM : android.view.HapticFeedbackConstants.LONG_PRESS);
        if (searched && lastInput != null) runSearch(lastInput);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (previewer != null) previewer.release();
        detachMini();
        if (isFinishing()) com.orvid.play.Session.release();
    }
}
