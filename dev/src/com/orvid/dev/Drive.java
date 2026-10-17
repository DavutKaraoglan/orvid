package com.orvid.dev;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class Drive {

    private static volatile Activity current;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public static final class Init extends ContentProvider {
        @Override
        public boolean onCreate() {
            Thread poll = new Thread(Drive::poll, "orvid-dev-drive");
            poll.setDaemon(true);
            poll.start();
            Application app = (Application) getContext().getApplicationContext();
            app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override
                public void onActivityResumed(Activity a) {
                    if (!(a instanceof Cmd)) current = a;
                }

                @Override
                public void onActivityPaused(Activity a) {
                }

                @Override
                public void onActivityCreated(Activity a, Bundle b) {
                }

                @Override
                public void onActivityStarted(Activity a) {
                }

                @Override
                public void onActivityStopped(Activity a) {
                }

                @Override
                public void onActivitySaveInstanceState(Activity a, Bundle b) {
                }

                @Override
                public void onActivityDestroyed(Activity a) {
                    if (current == a) current = null;
                }
            });
            return true;
        }

        @Override
        public Cursor query(Uri u, String[] p, String s, String[] a, String o) {
            return null;
        }

        @Override
        public String getType(Uri u) {
            return null;
        }

        @Override
        public Uri insert(Uri u, ContentValues v) {
            return null;
        }

        @Override
        public int delete(Uri u, String s, String[] a) {
            return 0;
        }

        @Override
        public int update(Uri u, ContentValues v, String s, String[] a) {
            return 0;
        }
    }

    public static final class Cmd extends Activity {
        @Override
        protected void onCreate(Bundle state) {
            super.onCreate(state);
            Intent intent = getIntent();
            finish();
            overridePendingTransition(0, 0);
            MAIN.postDelayed(() -> {
                try {
                    run(intent);
                } catch (Throwable e) {
                    DevUpdate.async("drive FAIL " + e);
                }
            }, SETTLE_MS);
        }
    }

    private static final long SETTLE_MS = 400;

    private static void poll() {
        while (true) {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:8471/cmd")
                        .openConnection(java.net.Proxy.NO_PROXY);
                c.setConnectTimeout(3000);
                c.setReadTimeout(15000);
                if (c.getResponseCode() == 200) {
                    String body = new String(c.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    Intent intent = new Intent();
                    for (String pair : body.split("&")) {
                        int eq = pair.indexOf('=');
                        if (eq <= 0) continue;
                        intent.putExtra(java.net.URLDecoder.decode(pair.substring(0, eq), "UTF-8"),
                                java.net.URLDecoder.decode(pair.substring(eq + 1), "UTF-8"));
                    }
                    MAIN.post(() -> {
                        try {
                            run(intent);
                        } catch (Throwable e) {
                            DevUpdate.async("drive FAIL " + e);
                        }
                    });
                }
                c.disconnect();
            } catch (Exception e) {
                SystemClock.sleep(2000);
            }
        }
    }

    private static void run(Intent intent) throws Exception {
        String cmd = intent.getStringExtra("cmd");
        Activity a = current;
        if (a == null) {
            DevUpdate.async("drive no activity");
            return;
        }
        View root = a.getWindow().getDecorView();
        switch (cmd == null ? "" : cmd) {
            case "shot":
                shot(a, intent.getStringExtra("name"));
                break;
            case "tap":
                gesture(root, f(intent, "x"), f(intent, "y"), f(intent, "x"), f(intent, "y"), 60);
                break;
            case "long":
                gesture(root, f(intent, "x"), f(intent, "y"), f(intent, "x"), f(intent, "y"), 900);
                break;
            case "swipe":
                gesture(root, f(intent, "x"), f(intent, "y"), f(intent, "x2"), f(intent, "y2"),
                        intent.getStringExtra("ms") != null ? Integer.parseInt(intent.getStringExtra("ms")) : intent.getIntExtra("ms", 300));
                break;
            case "click": {
                int id = a.getResources().getIdentifier(intent.getStringExtra("id"), "id", a.getPackageName());
                View v = null;
                if (id != 0) {
                    for (View r : roots(root)) if (v == null) v = r.findViewById(id);
                    if (v == null) v = root.findViewById(id);
                }
                if (v == null) throw new IllegalArgumentException("no view " + intent.getStringExtra("id"));
                v.performClick();
                break;
            }
            case "search": {
                java.lang.reflect.Method m = a.getClass().getDeclaredMethod("runSearch", String.class);
                m.setAccessible(true);
                m.invoke(a, intent.getStringExtra("q"));
                break;
            }
            case "home": {
                for (View w : roots(root)) {
                    w.dispatchKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_BACK));
                    w.dispatchKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_BACK));
                }
                if (!(a instanceof com.orvid.ui.MainActivity)) {
                    a.finish();
                    break;
                }
                java.lang.reflect.Method m = a.getClass().getDeclaredMethod("goHome");
                m.setAccessible(true);
                m.invoke(a);
                break;
            }
            case "back": {
                java.util.List<View> top = roots(root);
                if (top.isEmpty()) {
                    a.onBackPressed();
                } else {
                    View w = top.get(top.size() - 1);
                    w.dispatchKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_BACK));
                    w.dispatchKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_BACK));
                }
                break;
            }
            default:
                throw new IllegalArgumentException("unknown cmd " + cmd);
        }
        DevUpdate.async("drive ok " + cmd + " on " + a.getClass().getSimpleName());
    }

    private static float f(Intent i, String key) {
        return Float.parseFloat(i.getStringExtra(key));
    }

    private static void gesture(View main, float sx1, float sy1, float sx2, float sy2, int ms) {
        java.util.List<View> top = roots(main);
        View root = top.isEmpty() ? main : top.get(top.size() - 1);
        int[] origin = new int[2];
        root.getLocationOnScreen(origin);
        float x1 = sx1 - origin[0], y1 = sy1 - origin[1], x2 = sx2 - origin[0], y2 = sy2 - origin[1];
        long down = SystemClock.uptimeMillis();
        root.dispatchTouchEvent(MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, x1, y1, 0));
        int steps = Math.max(1, ms / 16);
        for (int i = 1; i <= steps; i++) {
            final float t = (float) i / steps;
            final long at = down + (long) (ms * t);
            MAIN.postAtTime(() -> root.dispatchTouchEvent(MotionEvent.obtain(down, at, MotionEvent.ACTION_MOVE,
                    x1 + (x2 - x1) * t, y1 + (y2 - y1) * t, 0)), at);
        }
        MAIN.postAtTime(() -> root.dispatchTouchEvent(MotionEvent.obtain(down, down + ms + 16, MotionEvent.ACTION_UP,
                x2, y2, 0)), down + ms + 16);
    }

    @SuppressWarnings("unchecked")
    private static java.util.List<View> roots(View main) {
        java.util.List<View> out = new java.util.ArrayList<>();
        try {
            Class<?> global = Class.forName("android.view.WindowManagerGlobal");
            Object instance = global.getMethod("getInstance").invoke(null);
            java.lang.reflect.Field views = global.getDeclaredField("mViews");
            views.setAccessible(true);
            boolean above = false;
            for (View v : (java.util.List<View>) views.get(instance)) {
                if (v == main) {
                    above = true;
                    continue;
                }
                if (above && v.isShown() && v.getWidth() > 0) out.add(v);
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static void shot(Activity a, String name) {
        View root = a.getWindow().getDecorView();
        if (root.getWidth() == 0 || root.getHeight() == 0) {
            MAIN.postDelayed(() -> shot(a, name), SETTLE_MS);
            return;
        }
        java.util.List<View> extra = roots(root);
        StringBuilder tree = new StringBuilder();
        dump(a, root, 0, tree, origin(root));
        for (View v : extra) {
            tree.append("--- window ").append(v.getClass().getSimpleName()).append('\n');
            dump(a, v, 0, tree, origin(v));
        }
        Bitmap bitmap = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
        int[] base = new int[2];
        root.getLocationOnScreen(base);
        root.draw(canvas);
        textures(root, canvas, base);
        for (View v : extra) {
            int[] at = new int[2];
            v.getLocationOnScreen(at);
            canvas.save();
            canvas.translate(at[0] - base[0], at[1] - base[1]);
            v.draw(canvas);
            canvas.restore();
            textures(v, canvas, base);
        }
        send(bitmap, tree, name, 0);
    }

    private static void textures(View v, android.graphics.Canvas canvas, int[] base) {
        if (!v.isShown()) return;
        if (v instanceof android.view.TextureView && ((android.view.TextureView) v).isAvailable()) {
            Bitmap frame = ((android.view.TextureView) v).getBitmap();
            if (frame != null) {
                int[] at = new int[2];
                v.getLocationOnScreen(at);
                canvas.drawBitmap(frame, at[0] - base[0], at[1] - base[1], null);
                if (v.getParent() instanceof ViewGroup) {
                    ViewGroup parent = (ViewGroup) v.getParent();
                    for (int i = parent.indexOfChild(v) + 1; i < parent.getChildCount(); i++) {
                        View above = parent.getChildAt(i);
                        if (above.getVisibility() != View.VISIBLE || above.getAlpha() <= 0f) continue;
                        int[] pos = new int[2];
                        above.getLocationOnScreen(pos);
                        if (above.getAlpha() < 1f) {
                            canvas.saveLayerAlpha(null, Math.round(above.getAlpha() * 255));
                        } else {
                            canvas.save();
                        }
                        canvas.translate(pos[0] - base[0], pos[1] - base[1]);
                        above.draw(canvas);
                        canvas.restore();
                    }
                }
            }
            return;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) textures(g.getChildAt(i), canvas, base);
        }
    }

    private static void send(Bitmap bitmap, StringBuilder tree, String name, int result) {
        new Thread(() -> {
            try {
                ByteArrayOutputStream png = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, png);
                upload("shot", name, png.toByteArray());
                upload("tree", name, tree.toString().getBytes(StandardCharsets.UTF_8));
                DevUpdate.async("drive shot " + name + " copy=" + result);
            } catch (Exception e) {
                DevUpdate.async("drive shot FAIL " + e);
            }
        }).start();
    }

    private static int[] origin(View root) {
        int[] at = new int[2];
        root.getLocationOnScreen(at);
        return at;
    }

    private static void dump(Activity a, View v, int depth, StringBuilder out, int[] origin) {
        if (v.getVisibility() == View.GONE) return;
        Rect r = new Rect();
        boolean shown = v.getGlobalVisibleRect(r);
        r.offset(origin[0], origin[1]);
        String id = "";
        if (v.getId() != View.NO_ID) {
            try {
                id = a.getResources().getResourceEntryName(v.getId());
            } catch (Exception ignored) {
            }
        }
        for (int i = 0; i < depth; i++) out.append("  ");
        out.append(v.getClass().getSimpleName());
        if (!id.isEmpty()) out.append(" #").append(id);
        out.append(shown ? " " + r.toShortString() : " offscreen");
        if (v.getVisibility() == View.INVISIBLE) out.append(" invisible");
        if (v.getAlpha() < 1f) out.append(" a=").append(v.getAlpha());
        if (v instanceof TextView) {
            CharSequence t = ((TextView) v).getText();
            if (t != null && t.length() > 0) out.append(" \"").append(t.length() > 60 ? t.subSequence(0, 60) + "…" : t).append('"');
        }
        out.append('\n');
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) dump(a, g.getChildAt(i), depth + 1, out, origin);
        }
    }

    private static void upload(String kind, String name, byte[] body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:8471/" + kind + "?name="
                + URLEncoder.encode(name == null ? "shot" : name, "UTF-8")).openConnection(java.net.Proxy.NO_PROXY);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setFixedLengthStreamingMode(body.length);
        try (OutputStream out = c.getOutputStream()) {
            out.write(body);
        }
        c.getResponseCode();
        c.disconnect();
    }
}
