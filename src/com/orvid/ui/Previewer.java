package com.orvid.ui;

import android.content.Context;
import android.view.TextureView;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;

import com.orvid.net.Fetch;
import com.orvid.play.CircuitDataSource;
import com.orvid.play.Resolve;

import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class Previewer {

    private static final ExecutorService POOL = Executors.newSingleThreadExecutor();

    private final Context context;
    private ExoPlayer player;
    private TextureView surface;
    private FrameLayout host;
    private int token;

    Previewer(Context context) {
        this.context = context.getApplicationContext();
    }

    void start(FrameLayout container, StreamInfoItem item) {
        stop();
        final int mine = ++token;
        host = container;
        surface = new TextureView(context);
        surface.setAlpha(0f);
        container.addView(surface, 1, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        if (player == null) {
            player = new ExoPlayer.Builder(context).build();
            player.setVolume(0f);
            player.setRepeatMode(Player.REPEAT_MODE_ONE);
            player.addListener(new Player.Listener() {
                @Override
                public void onRenderedFirstFrame() {
                    if (surface != null) surface.animate().alpha(1f).setDuration(150).start();
                }
            });
        }
        player.setVideoTextureView(surface);
        final long duration = item.getDuration();
        final String url = item.getUrl();
        playing = url;
        POOL.execute(() -> {
            try {
                if (mine != token) return;
                Resolve.Stream s = Resolve.preview(url);
                if (s == null || s.direct.url == null || mine != token) return;
                CircuitDataSource.Factory data = new CircuitDataSource.Factory(s.circuit, Fetch.USER_AGENT, s.direct.referer);
                MediaItem media = MediaItem.fromUri(s.direct.url);
                MediaSource source = s.direct.hls ? new HlsMediaSource.Factory(data).createMediaSource(media)
                        : new ProgressiveMediaSource.Factory(data).createMediaSource(media);
                surface.post(() -> {
                    if (mine != token || player == null) return;
                    long start = duration > 120 ? duration * 150 : 0;
                    player.setMediaSource(source, start);
                    player.prepare();
                    player.setPlayWhenReady(true);
                });
            } catch (Exception ignored) {
            }
        });
    }

    long position(String url) {
        if (player == null || url == null || !url.equals(playing)) return -1;
        return player.getCurrentPosition();
    }

    private String playing;

    void stop() {
        playing = null;
        token++;
        if (player != null) {
            player.stop();
            player.clearVideoTextureView(surface);
        }
        if (host != null && surface != null) host.removeView(surface);
        surface = null;
        host = null;
    }

    void release() {
        stop();
        if (player != null) player.release();
        player = null;
    }
}
