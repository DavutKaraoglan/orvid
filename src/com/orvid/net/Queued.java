package com.orvid.net;

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

public final class Queued<T> extends FutureTask<T> {

    private volatile boolean started;

    public Queued(Callable<T> work) {
        super(work);
    }

    @Override
    public void run() {
        started = true;
        super.run();
    }

    public boolean waiting() {
        return !started && !isDone();
    }
}
