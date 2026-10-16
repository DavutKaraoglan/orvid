package com.orvid.app;

import android.app.Application;
import android.content.SharedPreferences;

import com.orvid.image.Images;
import com.orvid.net.Fetch;
import com.orvid.tor.Tor;
import com.orvid.yt.Visionos;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.localization.Localization;

import java.io.File;
import java.util.Locale;

public final class OrvidApp extends Application {

    private static OrvidApp instance;
    private Images images;
    private SharedPreferences prefs;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        CrashLog.install(this);
        new Thread(() -> TempLogs.sweep(this), "orvid-sweep").start();
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        NewPipe.init(Fetch.get());
        images = new Images(new File(getCacheDir(), "thumbs"));
        if (prefs.contains("mode")) prefs.edit().remove("mode").apply();
        new File(getFilesDir(), "history.json").delete();
        images.clearDisk();
        Visionos.addWarmer(com.orvid.vk.Vk::warm);
        applyTor(true);
    }

    public static OrvidApp get() {
        return instance;
    }

    public Images images() {
        return images;
    }

    public boolean debugOverlay() {
        return prefs.getBoolean("debug", false);
    }

    public void setDebugOverlay(boolean on) {
        prefs.edit().putBoolean("debug", on).apply();
    }

    public void restartTor() {
        applyTor(true);
    }

    public int layoutMode() {
        return prefs.getInt("layout", 0);
    }

    public void setLayoutMode(int mode) {
        prefs.edit().putInt("layout", mode).apply();
    }



    private void applyTor(boolean on) {
        Fetch.setTorMode(on);
        Locale locale = Locale.getDefault();
        String country = locale.getCountry().isEmpty() ? "US" : locale.getCountry();
        NewPipe.setupLocalization(Localization.fromLocale(locale),
                on ? ContentCountry.DEFAULT : new ContentCountry(country));
        if (on) {
            File dir = new File(getFilesDir(), "tor");
            dir.mkdirs();
            String binary = getApplicationInfo().nativeLibraryDir + "/libtor.so";
            Tor.get().start(dir, binary, android.os.Process.myPid());
            File dachDir = new File(getFilesDir(), "tor-dach");
            Tor.dach().prepare(dachDir, binary, android.os.Process.myPid(), () -> seedDach(dir, dachDir));
            warmCircuits();
        } else {
            Tor.get().stop();
            Tor.dach().stop();
        }
        Visionos.dropPool();
        Visionos.refill();
    }

    private void seedDach(File main, File dach) {
        File data = new File(dach, "data");
        data.mkdirs();
        try (java.io.InputStream in = getResources().openRawResource(com.orvid.R.raw.geoip_dach);
             java.io.OutputStream out = new java.io.FileOutputStream(new File(dach, "geoip"))) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            new java.io.FileOutputStream(new File(dach, "geoip6")).close();
        } catch (java.io.IOException ignored) {
        }
        for (String name : new String[]{"cached-certs", "cached-microdesc-consensus", "cached-microdescs"}) {
            File from = new File(main, "data/" + name);
            File to = new File(data, name);
            if (!from.isFile() || to.isFile() && to.lastModified() >= from.lastModified()) continue;
            try {
                java.nio.file.Files.copy(from.toPath(), to.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.io.IOException ignored) {
            }
        }
    }

    private static void warmCircuits() {
        new Thread(() -> {
            try {
                if (!Tor.get().awaitReady(120000)) return;
            } catch (InterruptedException e) {
                return;
            }
            for (int port : Tor.get().ports()) {
                new Thread(() -> {
                    try {
                        java.net.Proxy proxy = new java.net.Proxy(java.net.Proxy.Type.HTTP,
                                new java.net.InetSocketAddress("127.0.0.1", port));
                        java.net.HttpURLConnection c = (java.net.HttpURLConnection)
                                new java.net.URL("https://check.torproject.org/api/ip").openConnection(proxy);
                        c.setConnectTimeout(15000);
                        c.setReadTimeout(15000);
                        c.setRequestProperty("User-Agent", Fetch.USER_AGENT);
                        try (java.io.InputStream in = c.getInputStream()) {
                            in.readAllBytes();
                        }
                        c.disconnect();
                    } catch (Exception ignored) {
                    }
                }).start();
            }
        }).start();
    }

    public void wipeAndExit() {
        try {
            com.orvid.play.Session.release();
        } catch (Exception ignored) {
        }
        try {
            com.orvid.tor.Tor.get().stop();
            com.orvid.tor.Tor.dach().stop();
        } catch (Exception ignored) {
        }
        try {
            android.webkit.CookieManager.getInstance().removeAllCookies(null);
            android.webkit.CookieManager.getInstance().flush();
            android.webkit.WebStorage.getInstance().deleteAllData();
        } catch (Exception ignored) {
        }
        wipe(getCacheDir());
        wipe(getDir("webview", MODE_PRIVATE));
        wipe(getCodeCacheDir());
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(0);
    }

    private static void wipe(java.io.File dir) {
        java.io.File[] files = dir == null ? null : dir.listFiles();
        if (files == null) return;
        for (java.io.File f : files) {
            if (f.isDirectory()) wipe(f);
            f.delete();
        }
    }
}
