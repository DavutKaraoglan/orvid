package com.orvid.app;

import android.app.Activity;
import android.app.Application;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.WindowManager;

import com.orvid.ui.LockActivity;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public final class AppLock implements Application.ActivityLifecycleCallbacks {

    private static final String KEY = "app_lock";

    private final SharedPreferences prefs;
    private final List<WeakReference<Activity>> alive = new ArrayList<>();
    private boolean locked;
    private boolean prompting;
    private int started;

    AppLock(SharedPreferences prefs) {
        this.prefs = prefs;
        locked = enabled();
    }

    public boolean enabled() {
        return prefs.getBoolean(KEY, false);
    }

    public static boolean available(Context context) {
        KeyguardManager keyguard = context.getSystemService(KeyguardManager.class);
        return keyguard != null && keyguard.isDeviceSecure();
    }

    public void setEnabled(boolean on) {
        prefs.edit().putBoolean(KEY, on).apply();
        if (!on) locked = false;
        for (WeakReference<Activity> ref : alive) {
            Activity a = ref.get();
            if (a != null) secure(a, on);
        }
    }

    public void unlocked() {
        locked = false;
        prompting = false;
    }

    private static void secure(Activity a, boolean on) {
        if (on) a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        else a.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle state) {
        alive.add(new WeakReference<>(activity));
        if (enabled()) secure(activity, true);
    }

    @Override
    public void onActivityStarted(Activity activity) {
        started++;
        if (!locked || !enabled() || activity instanceof LockActivity) return;
        if (!available(activity)) {
            locked = false;
            return;
        }
        if (prompting) return;
        prompting = true;
        activity.startActivity(new Intent(activity, LockActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION));
        activity.overridePendingTransition(0, 0);
    }

    @Override
    public void onActivityResumed(Activity activity) {
    }

    @Override
    public void onActivityPaused(Activity activity) {
    }

    @Override
    public void onActivityStopped(Activity activity) {
        started--;
        if (started <= 0 && enabled() && !(activity instanceof LockActivity && prompting)) locked = true;
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle state) {
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        alive.removeIf(ref -> ref.get() == null || ref.get() == activity);
        if (activity instanceof LockActivity && locked) prompting = false;
    }
}
