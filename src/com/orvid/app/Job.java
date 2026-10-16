package com.orvid.app;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class Job<T> implements Runnable {

    private static final ExecutorService POOL = Executors.newFixedThreadPool(6);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private final Activity owner;
    private volatile boolean cancelled;

    protected Job(Activity owner) {
        this.owner = owner;
    }

    protected abstract T load() throws Exception;

    protected abstract void show(T result);

    protected void fail(Exception error) {
    }

    public final Job<T> start() {
        POOL.execute(this);
        return this;
    }

    public final void cancel() {
        cancelled = true;
    }

    @Override
    public final void run() {
        try {
            final T result = load();
            deliver(new Runnable() {
                @Override
                public void run() {
                    show(result);
                }
            });
        } catch (Throwable t) {
            final Exception e = t instanceof Exception ? (Exception) t : new RuntimeException(t);
            deliver(new Runnable() {
                @Override
                public void run() {
                    fail(e);
                }
            });
        }
    }

    private void deliver(final Runnable step) {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                if (cancelled || owner.isFinishing() || owner.isDestroyed()) {
                    com.orvid.net.Diag.mark("job result dropped: cancelled=" + cancelled
                            + " finishing=" + owner.isFinishing() + " destroyed=" + owner.isDestroyed());
                    return;
                }
                step.run();
            }
        });
    }
}
