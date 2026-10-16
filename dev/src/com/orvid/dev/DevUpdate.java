package com.orvid.dev;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.os.Bundle;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.net.URLEncoder;

public final class DevUpdate extends Activity {

    static final String SERVER = "http://127.0.0.1:8471/";

    static void async(String message) {
        new Thread(() -> report(message)).start();
    }

    static void report(String message) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(SERVER + "status?m="
                    + URLEncoder.encode(message, "UTF-8")).openConnection(Proxy.NO_PROXY);
            c.getResponseCode();
            c.disconnect();
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (handleResult(getIntent())) return;
        try {
            android.content.pm.PackageInfo me = getPackageManager().getPackageInfo(getPackageName(), 0);
            final long installed = me.lastUpdateTime;
            new Thread(() -> report("running build installed at " + installed / 1000
                    + " installer=" + getPackageManager().getInstallerPackageName(getPackageName()))).start();
        } catch (Exception ignored) {
        }
        getPackageManager().getPackageInstaller().registerSessionCallback(new PackageInstaller.SessionCallback() {
            public void onCreated(int id) { }
            public void onBadgingChanged(int id) { }
            public void onActiveChanged(int id, boolean active) { new Thread(() -> report("session active " + active)).start(); }
            public void onProgressChanged(int id, float progress) { }
            public void onFinished(int id, boolean success) { new Thread(() -> report("session finished " + success)).start(); }
        }, new android.os.Handler(android.os.Looper.getMainLooper()));
        new Thread(this::install).start();
    }

    static final String RESULT = "com.orvid.dev.RESULT";

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleResult(intent);
    }

    private boolean handleResult(Intent intent) {
        if (intent == null || !RESULT.equals(intent.getAction())) return false;
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        new Thread(() -> report("status " + status + " " + message)).start();
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) startActivity(confirm);
        }
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this::finish, 1500);
        return true;
    }

    private void install() {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(SERVER + "orvid.apk").openConnection(Proxy.NO_PROXY);
            long size = c.getContentLengthLong();
            PackageInstaller installer = getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams params =
                    new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setAppPackageName(getPackageName());
            if (Build.VERSION.SDK_INT >= 31) {
                params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
            }
            int id = installer.createSession(params);
            try (PackageInstaller.Session session = installer.openSession(id)) {
                try (InputStream in = c.getInputStream(); OutputStream out = session.openWrite("orvid.apk", 0, size)) {
                    byte[] buf = new byte[65536];
                    for (int n = in.read(buf); n > 0; n = in.read(buf)) out.write(buf, 0, n);
                    session.fsync(out);
                }
                Intent done = new Intent(this, DevUpdate.class).setAction(RESULT);
                int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
                session.commit(PendingIntent.getActivity(this, id, done, flags).getIntentSender());
            }
            report("committed " + size / 1024 + "KB");
        } catch (Exception e) {
            report("failed " + e);
        }
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this::finish, 60000);
    }
}
