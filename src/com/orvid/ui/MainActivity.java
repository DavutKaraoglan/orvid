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
        title.setOnClickListener(v -> logoTapped());
        field = findViewById(R.id.search_field);
        searchBox = findViewById(R.id.search_box);
        clearButton = findViewById(R.id.search_clear);
        clearButton.setOnClickListener(v -> {
            if (field.length() > 0) field.setText("");
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
        addRepo = findViewById(R.id.add_repo);
        addRepo.setOnClickListener(v -> RepoDialog.add(this, this::repoChanged));
        correction = findViewById(R.id.correction);
        correction.setOnClickListener(v -> {
            if (correctedQuery == null) return;
            String fixed = correctedQuery;
            field.setText(fixed);
            runSearch(fixed);
        });

        filters = Filters.current != null ? Filters.current : Filters.load(getSharedPreferences("settings", MODE_PRIVATE));
        Filters.current = filters;
        com.orvid.yt.Related.safeSearch = filters.hideAdult;
        adapter = new VideoAdapter(this, OrvidApp.get().layoutMode(), item -> {
            com.orvid.ext.ExtSource ext = com.orvid.ext.Registry.forUrl(item.getUrl());
            if (ext != null ? ext.webPlayback : com.orvid.web.WebSites.forUrl(item.getUrl()) != null || com.orvid.adult.Tubes.webOnly(item.getUrl())) {
                startActivity(new Intent(this, WebPlayerActivity.class).putExtra(WebPlayerActivity.EXTRA_URL, item.getUrl()));
                return;
            }
            openingPlayer = true;
            PlayerActivity.clearBack();
            long at = adapter.previewPosition(item.getUrl());
            startActivity(new Intent(this, PlayerActivity.class)
                        .putExtra(PlayerActivity.EXTRA_START_MS, Math.max(0, at))
                        .putExtra(PlayerActivity.EXTRA_TITLE, item.getName())
                        .putExtra(PlayerActivity.EXTRA_AVATAR, Ui.thumbnail(item.getUploaderAvatars(), Ui.dp(this, 32)))
                        .putExtra(PlayerActivity.EXTRA_URL, item.getUrl())
                        .putExtra(PlayerActivity.EXTRA_SERVICE, item.getServiceId())
                        .putExtra(PlayerActivity.EXTRA_THUMB, Ui.thumbnail(item.getThumbnails(), 640)));
            overridePendingTransition(0, 0);
        });
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
                        list.removeCallbacks(devRefresh);
                        if (com.orvid.app.DevInfo.on) list.postDelayed(devRefresh, DEV_REFRESH_MS);
                        if (searched && lastInput != null) runSearch(lastInput);
                        else if (pendingQuery != null) repoChanged();
                        else if (!searched) showHome();
                    }
                }));
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                adapter.setIdle(scrollState == SCROLL_STATE_IDLE);
                if (scrollState == SCROLL_STATE_TOUCH_SCROLL) browsed = true;
                list.removeCallbacks(prepareVisible);
                if (scrollState == SCROLL_STATE_IDLE) list.postDelayed(prepareVisible, PREPARE_IDLE_MS);
            }

            @Override
            public void onScroll(AbsListView view, int first, int visible, int total) {
                adapter.checkPreview();
                if (total > 0 && first + visible >= total - 6) loadMore();
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
                boolean empty = s.length() == 0;
                clearButton.setImageResource(empty ? R.drawable.ic_search : R.drawable.ic_close);
                clearButton.setContentDescription(getString(empty ? R.string.search : R.string.clear));
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

        @Override
        public void onVideoSizeChanged(androidx.media3.common.VideoSize size) {
            fitMini(size);
        }

        @Override
        public void onRenderedFirstFrame() {
            View cover = findViewById(R.id.mini_cover);
            cover.removeCallbacks(showMiniCover);
            cover.setVisibility(View.GONE);
        }
    };

    private static final long MINI_COVER_DELAY_MS = 500;
    private final Runnable showMiniCover = () -> findViewById(R.id.mini_cover).setVisibility(View.VISIBLE);

    private void fitMini(androidx.media3.common.VideoSize size) {
        View box = (View) miniVideo.getParent();
        int w = box.getLayoutParams().width;
        int h = box.getLayoutParams().height;
        android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(w, h, android.view.Gravity.CENTER);
        if (size.width > 0 && size.height > 0) {
            float ratio = size.width * size.pixelWidthHeightRatio / size.height;
            if (ratio > (float) w / h) lp.height = Math.round(w / ratio);
            else lp.width = Math.round(h * ratio);
        }
        miniVideo.setLayoutParams(lp);
    }

    private void freshMiniSurface() {
        android.view.ViewGroup box = (android.view.ViewGroup) miniVideo.getParent();
        int index = box.indexOfChild(miniVideo);
        box.removeView(miniVideo);
        miniVideo = new android.view.TextureView(this);
        miniVideo.setId(R.id.mini_video);
        box.addView(miniVideo, index);
    }

    private void attachMini() {
        androidx.media3.exoplayer.ExoPlayer p = com.orvid.play.Session.player();
        if (p == null) {
            mini.setVisibility(View.GONE);
            return;
        }
        mini.setVisibility(View.VISIBLE);
        ((TextView) findViewById(R.id.mini_title)).setText(com.orvid.play.Session.title);
        ((TextView) findViewById(R.id.mini_author)).setText(com.orvid.play.Session.author);
        freshMiniSurface();
        fitMini(p.getVideoSize());
        android.widget.ImageView cover = findViewById(R.id.mini_cover);
        cover.setVisibility(View.GONE);
        cover.removeCallbacks(showMiniCover);
        cover.postDelayed(showMiniCover, MINI_COVER_DELAY_MS);
        int coverWidth = cover.getLayoutParams().width > 0 ? cover.getLayoutParams().width : Ui.dp(this, 150);
        OrvidApp.get().images().load(cover, com.orvid.play.Session.thumb, coverWidth, com.orvid.play.Session.url());
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

    private int logoTaps;
    private long logoTapAt;

    private void logoTapped() {
        long now = System.currentTimeMillis();
        logoTaps = now - logoTapAt < 600 ? logoTaps + 1 : 1;
        logoTapAt = now;
        if (logoTaps < 5) return;
        logoTaps = 0;
        boolean on = !com.orvid.app.DevInfo.on;
        OrvidApp.get().setDevMode(on);
        android.widget.Toast.makeText(this, on ? R.string.dev_mode_on : R.string.dev_mode_off, android.widget.Toast.LENGTH_SHORT).show();
        list.removeCallbacks(devRefresh);
        if (on) list.postDelayed(devRefresh, DEV_REFRESH_MS);
        else findViewById(R.id.dev_status).setVisibility(View.GONE);
        adapter.notifyDataSetChanged();
    }

    private final Runnable devRefresh = new Runnable() {
        @Override
        public void run() {
            if (!com.orvid.app.DevInfo.on) return;
            if (searched && adapter.getCount() > 0) adapter.notifyDataSetChanged();
            list.postDelayed(this, DEV_REFRESH_MS);
        }
    };
    private static final long DEV_REFRESH_MS = 2000;

    private final java.util.Map<Integer, String> devSources = new java.util.TreeMap<>();
    private long searchStarted;

    private void devStatus(int source, String state) {
        if (!com.orvid.app.DevInfo.on) return;
        String name = source == 0 ? "youtube" : source == 1 ? "dailymotion" : source == 2 ? "vk"
                : source - CORE < extras.size() ? extras.get(source - CORE).key : "src" + source;
        devSources.put(source, name + " " + state);
        TextView view = findViewById(R.id.dev_status);
        view.setText(android.text.TextUtils.join(" | ", devSources.values()));
        view.setVisibility(View.VISIBLE);
    }

    private void showHome() {
        adapter.set(Collections.<StreamInfoItem>emptyList());
        if (com.orvid.ext.Registry.empty()) showEmpty();
        else showMessage(getString(R.string.tagline));
    }

    private String pendingQuery;

    private void showEmpty() {
        message.setText(R.string.nothing_here);
        message.setVisibility(View.VISIBLE);
        addRepo.setVisibility(View.VISIBLE);
    }

    private void repoChanged() {
        addRepo.setVisibility(View.GONE);
        String again = pendingQuery;
        pendingQuery = null;
        if (again != null && !com.orvid.ext.Registry.empty()) runSearch(again);
        else if (query != null) runSearch(query.raw);
        else showHome();
    }

    @Override
    protected void onPause() {
        super.onPause();
        detachMini();
        adapter.releasePreview();
    }

    @Override
    protected void onResume() {
        super.onResume();
        list.removeCallbacks(devRefresh);
        if (com.orvid.app.DevInfo.on) list.postDelayed(devRefresh, DEV_REFRESH_MS);
        openingPlayer = false;
        attachMini();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (adapter != null && filters != null) adapter.pruneOff(filters);
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
    private View addRepo;
    private ImageButton clearButton;

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

    private List<Source> extras = new ArrayList<>();
    private double[] weights = weights(extras);
    private boolean[] tagged = tagged(extras);
    private final Object extraLock = new Object();

    private static boolean[] tagged(List<Source> extras) {
        boolean[] t = new boolean[CORE + extras.size()];
        for (int i = 0; i < extras.size(); i++) t[CORE + i] = extras.get(i).adult;
        return t;
    }
    private static final long HEDGE_MS = 1500;

    private static final class Batch {
        final List<StreamInfoItem> youtube = new ArrayList<>();
        final List<StreamInfoItem> dailymotion = new ArrayList<>();
        final List<StreamInfoItem> vk = new ArrayList<>();
        final List<List<StreamInfoItem>> extra = new ArrayList<>();
        final String[] extraNext;
        String vkNext;

        Batch(int extras) {
            extraNext = new String[extras];
        }
        String ytContinuation;
        boolean dmMore;
    }

    private int pendingSources;
    private Exception lastSourceError;

    private void runSearch(final String input) {
        String link = com.orvid.play.Generic.linkIn(input);
        if (link != null) {
            dismissSuggest();
            openingPlayer = true;
            startActivity(new Intent(this, PlayerActivity.class).putExtra(PlayerActivity.EXTRA_URL, link));
            return;
        }
        dismissSuggest();
        if (com.orvid.ext.Registry.empty()) {
            if (running != null) running.cancel();
            running = null;
            adapter.set(Collections.<StreamInfoItem>emptyList());
            pendingQuery = input;
            showEmpty();
            return;
        }
        OrvidApp.get().images().warm();
        if (running != null) running.cancel();
        running = null;
        adapter.set(Collections.<StreamInfoItem>emptyList());
        message.setVisibility(View.GONE);
        addRepo.setVisibility(View.GONE);
        correction.setVisibility(View.GONE);
        adapter.setSkeleton(8);
        continuation = null;
        circuit = null;
        dmMore = false;
        dmPage = 1;
        vkNext = null;
        vkCircuit = null;
        extras = Sources.current();
        weights = weights(extras);
        tagged = tagged(extras);
        extraNext = new String[extras.size()];
        extraCircuit = new String[extras.size()];
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
        emptyBatches = 0;
        browsed = false;
        prepared.clear();
        devSources.clear();
        searchStarted = android.os.SystemClock.uptimeMillis();
        findViewById(R.id.dev_status).setVisibility(View.GONE);
        pendingSources = 0;

        if (searchScope != null) searchScope.cancel();
        searchScope = new Fetch.Scope();
        extraQuery = q;
        synchronized (extraLock) {
            extraStarted = new Query[extras.size()];
        }
        List<Queued> jobs = new ArrayList<>();

        if (q.siteIs("youtube", "YouTube") && f.youtube && com.orvid.ext.Registry.engineOn("youtube")) {
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
                    runOnUiThread(() -> sourceFailed(generation, 0, e));
                }
            }));
        }
        if (q.siteIs("dailymotion", "Dailymotion") && f.dailymotion && com.orvid.ext.Registry.engineOn("dailymotion")) {
            pendingSources++;
            jobs.add(queued(1, 0, () -> {
                try {
                    Dailymotion.Page page = Dailymotion.search(Visionos.takeCircuit(), q.plainQuery, 1,
                            f.dailymotionParams(), f.type == Filters.TYPE_SHORTS);
                    runOnUiThread(() -> {
                        if (generation != searchGeneration) return;
                        dmMore = page.hasMore;
                        deliver(generation, q, f, 1, page.items);
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> sourceFailed(generation, 1, e));
                }
            }));
        }
        if (q.siteIs("vk", "VK") && f.vk && f.type != Filters.TYPE_SHORTS && com.orvid.ext.Registry.engineOn("vk")) {
            pendingSources++;
            jobs.add(queued(Query.CYRILLIC.equals(q.script()) ? 2 : 4, -0.8, () -> {
                try {
                    Hedge.Result<com.orvid.vk.Vk.Page> r = Hedge.race(com.orvid.vk.Vk.circuits(Visionos::takeCircuit),
                            1200, c -> com.orvid.vk.Vk.search(c, q.plainQuery, null));
                    runOnUiThread(() -> {
                        if (generation != searchGeneration) return;
                        vkNext = r.value.next;
                        vkCircuit = r.circuit;
                        deliver(generation, q, f, 2, r.value.items);
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> sourceFailed(generation, 2, e));
                }
            }));
        }
        for (int i = 0; i < extras.size(); i++) {
            final Source src = extras.get(i);
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
        synchronized (extraLock) {
            use = forced != null ? forced : extraQuery;
            extraStarted[slot] = use;
        }
        if (!src.wanted(f, use)) {
            runOnUiThread(() -> sourceFailed(generation, CORE + slot, null));
            return;
        }
        try {
            Hedge.Result<Source.Page> first = Hedge.call(Fetch.newCircuit("x"), () -> Fetch.newCircuit("x"),
                    VK_HEDGE_MS, c -> src.search(c, use.plainQuery, f, null));
            if (first.value.items.isEmpty()) {
                Diag.mark(src.key + " empty, asking again from another exit");
                Hedge.Result<Source.Page> again = Hedge.call(Fetch.newCircuit("x"), () -> Fetch.newCircuit("x"),
                        VK_HEDGE_MS, c -> src.search(c, use.plainQuery, f, null));
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
            runOnUiThread(() -> sourceFailed(generation, CORE + slot, e));
        }
    }

    private void secondWave(int generation, Query q2, Filters f) {
        query = q2;
        adapter.setQuery(q2);
        List<Queued> jobs = new ArrayList<>();
        synchronized (extraLock) {
            extraQuery = q2;
            for (int i = 0; i < extras.size(); i++) {
                final Source src = extras.get(i);
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
    private Query[] extraStarted = new Query[0];
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
    private String[] extraNext = new String[0];
    private String[] extraCircuit = new String[0];

    private static double[] weights(List<Source> extras) {
        double[] w = new double[CORE + extras.size()];
        w[0] = 1.0;
        w[1] = 0.7;
        w[2] = 0.8;
        for (int i = 0; i < extras.size(); i++) w[CORE + i] = extras.get(i).weight;
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
        devStatus(source, items.size() + " " + (android.os.SystemClock.uptimeMillis() - searchStarted) / 100 / 10.0 + "s");
        pendingSources--;
        if (arrived == null) {
            arrived = new ArrayList<>();
            for (int i = 0; i < weights.length; i++) arrived.add(new ArrayList<StreamInfoItem>());
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
                ranked = f.arrange(use, snapshot, weights, tagged);
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

    private boolean browsed;

    private void applyRanked(List<StreamInfoItem> ranked) {
        if (shown && !browsed && list.getFirstVisiblePosition() == 0) {
            adapter.set(ranked);
            com.orvid.play.Session.lastSearch = new ArrayList<>(ranked);
            return;
        }
        if (shown) {
            mergeTail(ranked);
            return;
        }
        if (ranked.isEmpty() && pendingSources > 0) return;
        shown = true;
        adapter.set(ranked);
        com.orvid.play.Session.lastSearch = new ArrayList<>(ranked);
        list.setSelection(0);
        list.removeCallbacks(prepareVisible);
        list.postDelayed(prepareVisible, PREPARE_IDLE_MS);
    }


    private static final int PREPARE_AHEAD = 2;
    private static final int PREPARE_LIGHT = 2;
    private static final long PREPARE_IDLE_MS = 1500;
    private static final long PREPARE_RETRY_MS = 800;
    private static final int PREPARE_RETRIES = 4;
    private int prepareTries;
    private final java.util.Set<String> prepared = new java.util.HashSet<>();
    private final Runnable prepareVisible = this::prepareVisible;

    private void prepareVisible() {
        if (!searched || list.getChildCount() == 0) return;
        if (OrvidApp.get().images().busy() && ++prepareTries <= PREPARE_RETRIES) {
            list.postDelayed(prepareVisible, PREPARE_RETRY_MS);
            return;
        }
        prepareTries = 0;
        int cols = VideoAdapter.columns(adapter.mode());
        int first = list.getFirstVisiblePosition();
        int center = list.getHeight() / 2;
        List<StreamInfoItem> rows = adapter.itemsInRows(first, list.getLastVisiblePosition() + 1);
        List<StreamInfoItem> wanted = new ArrayList<>();
        List<Object[]> byDistance = new ArrayList<>();
        int lastFull = -1;
        for (int c = 0; c < list.getChildCount(); c++) {
            View row = list.getChildAt(c);
            if (row.getTop() < 0 || row.getBottom() > list.getHeight()) continue;
            lastFull = c;
            int distance = Math.abs((row.getTop() + row.getBottom()) / 2 - center);
            for (int k = 0; k < cols; k++) {
                int index = c * cols + k;
                if (index >= rows.size()) break;
                StreamInfoItem item = rows.get(index);
                wanted.add(item);
                byDistance.add(new Object[] {item, distance});
            }
        }
        for (int index = (lastFull + 1) * cols, n = 0; index < rows.size() && n < PREPARE_AHEAD; index++, n++) {
            wanted.add(rows.get(index));
        }
        for (StreamInfoItem item : wanted) {
            if (prepared.add(item.getUrl())) com.orvid.play.Warm.link(item.getUrl());
        }
        byDistance.sort((x, y) -> Integer.compare((int) x[1], (int) y[1]));
        for (int i = 0; i < Math.min(byDistance.size(), 1 + PREPARE_LIGHT); i++) {
            com.orvid.play.Warm.prepare(((StreamInfoItem) byDistance.get(i)[0]).getUrl(), true);
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
        com.orvid.play.Session.lastSearch = new ArrayList<>(merged);
    }

    private void sourceFailed(int generation, int source, Exception e) {
        if (generation != searchGeneration) return;
        String why = e == null ? null : e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        devStatus(source, why == null ? "skipped" : "FAIL " + (why.length() > 40 ? why.substring(0, 40) : why));
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

    private static final int REFILL_BELOW = 6;
    private static final int MAX_EMPTY_BATCHES = 3;
    private int emptyBatches;

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
        final List<Source> sourcesNow = extras;
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
                Batch batch = new Batch(wantExtra.length);
                java.util.concurrent.Future<Dailymotion.Page> dm = !wantDm ? null
                        : PARALLEL.submit(inScope(scope, () -> Dailymotion.search(Visionos.takeCircuit(), q.plainQuery, nextDm, f.dailymotionParams(), f.type == Filters.TYPE_SHORTS)));
                java.util.concurrent.Future<com.orvid.vk.Vk.Page> vkf = wantVk == null || ownerVk == null ? null
                        : PARALLEL.submit(inScope(scope, () -> Fetch.retrying(ownerVk, c -> com.orvid.vk.Vk.search(c, q.plainQuery, wantVk))));
                batch.ytContinuation = token;
                if (token != null) {
                    try {
                        Search.Page p = Fetch.retrying(ownerCircuit, c -> Search.more(c, token));
                        batch.youtube.addAll(p.items);
                        batch.ytContinuation = p.continuation;
                    } catch (Exception ignored) {
                    }
                }
                batch.dmMore = wantDm;
                if (dm != null) {
                    try {
                        Dailymotion.Page page = dm.get();
                        batch.dailymotion.addAll(page.items);
                        batch.dmMore = page.hasMore;
                    } catch (Exception ignored) {
                    }
                }
                batch.vkNext = wantVk;
                if (vkf != null) {
                    try {
                        com.orvid.vk.Vk.Page page = vkf.get();
                        batch.vk.addAll(page.items);
                        batch.vkNext = page.next;
                    } catch (Exception ignored) {
                    }
                }
                List<java.util.concurrent.Future<Source.Page>> extraJobs = new ArrayList<>();
                for (int i = 0; i < sourcesNow.size(); i++) {
                    final Source src = sourcesNow.get(i);
                    final String want = wantExtra[i];
                    final String owner = ownerExtra[i];
                    extraJobs.add(want == null || owner == null ? null : PARALLEL.submit(
                            inScope(scope, () -> Fetch.retrying(owner, c -> src.search(c, q.plainQuery, f, want)))));
                }
                for (int i = 0; i < extraJobs.size(); i++) {
                    List<StreamInfoItem> got = new ArrayList<>();
                    batch.extraNext[i] = wantExtra[i];
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
                List<StreamInfoItem> fresh = f.arrange(q, lists, weights, tagged);
                adapter.add(fresh);
                loadingMore = false;
                if (fresh.size() < REFILL_BELOW) {
                    if (++emptyBatches <= MAX_EMPTY_BATCHES) list.post(MainActivity.this::loadMore);
                } else {
                    emptyBatches = 0;
                }
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
        addRepo.setVisibility(View.GONE);
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
        if (suppressSuggest || text.length() < 2 || !field.hasFocus() || com.orvid.search.Source.adultUnlocked
                || !com.orvid.ext.Registry.engineOn("youtube")) {
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

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent e) {
        if (panic.feed(this, e)) return true;
        if (pinch != null) pinch.onTouchEvent(e);
        return super.dispatchTouchEvent(e);
    }

    private void setAdult(boolean on) {
        com.orvid.search.Source.adultUnlocked = on;
        filters.hideAdult = !on;
        com.orvid.yt.Related.safeSearch = !on;
        getWindow().getDecorView().performHapticFeedback(android.os.Build.VERSION.SDK_INT >= 30
                ? android.view.HapticFeedbackConstants.CONFIRM : android.view.HapticFeedbackConstants.LONG_PRESS);
        if (searched && lastInput != null) runSearch(lastInput);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        detachMini();
        if (isFinishing()) com.orvid.play.Session.release();
    }
}
