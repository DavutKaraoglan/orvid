package com.orvid.ui;

import android.content.Context;

import com.orvid.R;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException;
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public final class Ui {

    private Ui() {
    }

    public static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static String duration(long seconds) {
        if (seconds <= 0) return "";
        long h = seconds / 3600;
        long m = seconds % 3600 / 60;
        long s = seconds % 60;
        return h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
                : String.format(Locale.ROOT, "%d:%02d", m, s);
    }

    public static String count(long n) {
        if (n < 0) return "";
        if (n < 1000) return Long.toString(n);
        String[] units = {"K", "M", "B"};
        double v = n;
        int u = -1;
        while (v >= 1000 && u < units.length - 1) {
            v /= 1000;
            u++;
        }
        return (v < 10 ? String.format(Locale.getDefault(), "%.1f", v)
                : String.format(Locale.getDefault(), "%.0f", v)) + units[u];
    }

    public static String joinDot(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(" · ");
            sb.append(p);
        }
        return sb.toString();
    }

    private static final java.util.regex.Pattern YT_THUMB =
            java.util.regex.Pattern.compile("^https://i\\.ytimg\\.com/vi(?:_webp)?/([A-Za-z0-9_-]{11})/");

    public static String thumbnail(List<Image> images, int targetWidth) {
        String picked = pickThumbnail(images, targetWidth);
        if (picked == null) return null;
        java.util.regex.Matcher m = YT_THUMB.matcher(picked);
        if (!m.find()) return picked;
        return "https://i.ytimg.com/vi_webp/" + m.group(1) + (targetWidth > 480 ? "/hq720.webp" : "/hqdefault.webp");
    }

    private static String pickThumbnail(List<Image> images, int targetWidth) {
        int enough = targetWidth <= 400 ? targetWidth * 3 / 4 : targetWidth;
        Image best = null;
        for (Image i : images) {
            if (best == null) {
                best = i;
                continue;
            }
            int w = i.getWidth() > 0 ? i.getWidth() : i.getHeight() * 16 / 9;
            int bw = best.getWidth() > 0 ? best.getWidth() : best.getHeight() * 16 / 9;
            boolean fits = w >= enough;
            boolean bestFits = bw >= enough;
            if (fits && (!bestFits || w < bw) || !fits && !bestFits && w > bw) best = i;
        }
        return best == null ? null : best.getUrl();
    }

    public static String error(Context context, Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof com.orvid.net.Fetch.TorUnavailable) return context.getString(R.string.error_tor);
        }
        if (e instanceof ReCaptchaException || e instanceof SignInConfirmNotBotException) {
            return context.getString(R.string.error_captcha);
        }
        if (e instanceof AgeRestrictedContentException) return context.getString(R.string.error_age);
        if (e instanceof ContentNotAvailableException) return context.getString(R.string.error_unavailable);
        if (e instanceof IOException) return context.getString(R.string.error_network);
        return e.getMessage() == null ? e.toString() : e.getMessage();
    }
}
