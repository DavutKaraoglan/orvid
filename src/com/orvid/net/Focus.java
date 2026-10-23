package com.orvid.net;

import java.io.InterruptedIOException;
import java.util.concurrent.ThreadFactory;

public final class Focus {

    private static final Object LOCK = new Object();
    private static final ThreadLocal<Boolean> BACKGROUND = new ThreadLocal<>();
    private static final long STALE_MS = 5000;
    private static final long POLL_MS = 250;
    private static boolean held;
    private static long heldAt;
    private static volatile int holds;

    private Focus() {
    }

    public static void hold(boolean on) {
        boolean changed;
        synchronized (LOCK) {
            changed = held != on;
            if (changed && on) holds++;
            held = on;
            heldAt = android.os.SystemClock.uptimeMillis();
            if (!on) LOCK.notifyAll();
        }
        if (changed) Diag.mark(on ? "focus: video first" : "focus: background free");
    }

    public static int holds() {
        return holds;
    }

    public static ThreadFactory background(String name) {
        return background(name, Thread.NORM_PRIORITY);
    }

    public static ThreadFactory background(String name, int priority) {
        return r -> {
            Thread t = new Thread(() -> {
                BACKGROUND.set(true);
                r.run();
            }, name);
            t.setDaemon(true);
            t.setPriority(priority);
            return t;
        };
    }

    static boolean backgroundThread() {
        return Boolean.TRUE.equals(BACKGROUND.get());
    }

    private static boolean holding() {
        return held && android.os.SystemClock.uptimeMillis() - heldAt < STALE_MS;
    }

    static void await(Fetch.Scope scope) throws java.io.IOException {
        boolean background = scope != null ? scope.background : Boolean.TRUE.equals(BACKGROUND.get());
        if (!background) return;
        synchronized (LOCK) {
            while (holding()) {
                if (scope != null && scope.cancelled()) throw new Fetch.Cancelled();
                try {
                    LOCK.wait(POLL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new InterruptedIOException("focus wait interrupted");
                }
            }
        }
    }
}
