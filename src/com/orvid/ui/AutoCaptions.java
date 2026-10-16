package com.orvid.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.TextView;

import androidx.media3.common.C;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.audio.DefaultAudioSink;

import com.orvid.R;
import com.orvid.net.Diag;
import com.orvid.net.Fetch;
import com.orvid.play.Speech;
import com.orvid.play.SpeechModel;
import com.orvid.play.SpeechTap;
import com.orvid.play.Translate;

import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class AutoCaptions implements Speech.Listener {

    static final int OFF = 0;
    static final int ORIGINAL = 1;
    static final int TRANSLATED = 2;

    static final SpeechTap TAP = new SpeechTap();
    private static final int LEAD_SECONDS = 12;
    private static volatile boolean lead;

    static final DefaultAudioSink.AudioTrackBufferSizeProvider BUFFER =
            (min, encoding, mode, frameSize, rate, bitrate, speed) -> {
                int normal = DefaultAudioSink.AudioTrackBufferSizeProvider.DEFAULT
                        .getBufferSizeInBytes(min, encoding, mode, frameSize, rate, bitrate, speed);
                if (!lead || mode != DefaultAudioSink.OUTPUT_MODE_PCM || frameSize <= 0 || rate <= 0) return normal;
                long wanted = (long) (frameSize * (long) rate * LEAD_SECONDS * Math.max(1.0, speed));
                return (int) Math.max(normal, Math.min(wanted, 16L * 1024 * 1024));
            };

    private static final class Line {
        final long start;
        final long end;
        final String text;

        Line(long start, long end, String text) {
            this.start = start;
            this.end = end;
            this.text = text;
        }
    }

    private final Context context;
    private final ExoPlayer player;
    private final TextView view;
    private final SharedPreferences prefs;
    private final TreeMap<Long, Line> lines = new TreeMap<>();
    private final ExecutorService translator = Executors.newSingleThreadExecutor();
    private final String target = Locale.getDefault().getLanguage();
    private final String circuit = Fetch.newCircuit("tr");
    private Speech speech;
    private volatile int mode = OFF;
    private boolean downloading;
    private String status;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (mode == OFF) return;
            show();
            view.postDelayed(this, 120);
        }
    };

    AutoCaptions(Context context, ExoPlayer player, TextView view) {
        this.context = context.getApplicationContext();
        this.player = player;
        this.view = view;
        this.prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    boolean available() {
        return Speech.available(context.getApplicationInfo().nativeLibraryDir);
    }

    int mode() {
        return mode;
    }

    String targetName() {
        return Locale.getDefault().getDisplayLanguage(Locale.getDefault());
    }

    void restore() {
        int saved = prefs.getInt("auto_captions", OFF);
        if (saved != OFF && available() && SpeechModel.ready(context.getFilesDir())) setMode(saved);
    }

    void setMode(int next) {
        prefs.edit().putInt("auto_captions", next).apply();
        if (next == OFF) {
            stop();
            return;
        }
        if (!available()) {
            status(context.getString(R.string.auto_captions_failed));
            return;
        }
        boolean wasOff = mode == OFF;
        mode = next;
        synchronized (lines) {
            lines.clear();
        }
        if (!SpeechModel.ready(context.getFilesDir())) {
            download();
            return;
        }
        begin(wasOff);
    }

    private void begin(boolean fresh) {
        if (speech == null) {
            speech = new Speech(this);
            try {
                speech.start(context.getApplicationInfo().nativeLibraryDir,
                        SpeechModel.file(context.getFilesDir()), "auto");
            } catch (Exception e) {
                Diag.mark("speech start FAIL " + e);
                speech = null;
                mode = OFF;
                status(context.getString(R.string.auto_captions_failed));
                return;
            }
        }
        TAP.setSink(speech);
        status(context.getString(R.string.auto_captions_listening));
        if (!lead) {
            lead = true;
            if (player.getMediaItemCount() > 0 && player.getPlaybackState() != androidx.media3.common.Player.STATE_IDLE) reprepare();
        }
        if (fresh) view.post(tick);
    }

    private boolean released;

    private final SpeechModel.Listener modelListener = new SpeechModel.Listener() {
        @Override
        public void update(long done, long total) {
            int pct = (int) (done * 100 / Math.max(1, total));
            view.post(() -> {
                if (!released && downloading) status(context.getString(R.string.auto_captions_downloading, pct));
            });
        }

        @Override
        public void finished(boolean ok) {
            view.post(() -> {
                downloading = false;
                if (released) return;
                if (ok) {
                    if (mode != OFF) begin(true);
                } else {
                    mode = OFF;
                    status(context.getString(R.string.auto_captions_failed));
                    view.postDelayed(() -> status(null), 3000);
                }
            });
        }
    };

    private void download() {
        if (downloading) return;
        downloading = true;
        SpeechModel.ensure(context.getFilesDir(), circuit, modelListener);
    }

    private void reprepare() {
        long at = Math.max(0, player.getCurrentPosition());
        boolean playing = player.getPlayWhenReady();
        player.stop();
        player.prepare();
        player.seekTo(at);
        player.setPlayWhenReady(playing);
    }

    void seeked() {
        if (speech != null) speech.clear();
    }

    private void stop() {
        mode = OFF;
        TAP.setSink(null);
        if (speech != null) speech.stop();
        speech = null;
        lead = false;
        synchronized (lines) {
            lines.clear();
        }
        view.removeCallbacks(tick);
        status(null);
    }

    void release() {
        released = true;
        SpeechModel.detach(modelListener);
        TAP.setSink(null);
        if (speech != null) speech.stop();
        speech = null;
        view.removeCallbacks(tick);
        translator.shutdownNow();
    }

    @Override
    public void segment(long startMs, long endMs, String language, String text) {
        if (mode == OFF) return;
        if (mode == TRANSLATED) {
            translator.execute(() -> {
                String shown = Translate.text(circuit, text, language, target);
                add(startMs, endMs, shown);
            });
        } else {
            add(startMs, endMs, text);
        }
    }

    private void add(long start, long end, String text) {
        synchronized (lines) {
            lines.put(start, new Line(start, Math.max(end, start + 1200), text));
            while (lines.size() > 400) lines.pollFirstEntry();
        }
        view.post(() -> {
            if (status != null && !downloading) status = null;
        });
    }

    private void status(String text) {
        status = text;
        if (text == null) {
            view.setText("");
            view.setVisibility(View.GONE);
        } else {
            view.setText(text);
            view.setVisibility(View.VISIBLE);
        }
    }

    private void show() {
        if (player.getCurrentTracks().isTypeSelected(C.TRACK_TYPE_TEXT)) return;
        if (status != null) return;
        long now = player.getCurrentPosition();
        Line current = null;
        synchronized (lines) {
            Map.Entry<Long, Line> e = lines.floorEntry(now);
            if (e != null && now <= e.getValue().end + 600) current = e.getValue();
        }
        if (current == null) {
            if (view.getVisibility() != View.GONE) {
                view.setText("");
                view.setVisibility(View.GONE);
            }
            return;
        }
        if (!current.text.contentEquals(view.getText())) view.setText(current.text);
        view.setVisibility(View.VISIBLE);
    }
}
