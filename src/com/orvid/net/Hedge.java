package com.orvid.net;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class Hedge {

    private static final ExecutorService POOL = Executors.newCachedThreadPool();

    private Hedge() {
    }

    public interface Task<T> {
        T run(String circuit) throws Exception;
    }

    public static final class Result<T> {
        public final T value;
        public final String circuit;

        Result(T value, String circuit) {
            this.value = value;
            this.circuit = circuit;
        }
    }

    public static <T> Result<T> call(String first, Supplier<String> spare, long delayMs, Task<T> task) throws Exception {
        CompletionService<Result<T>> done = new ExecutorCompletionService<>(POOL);
        Future<Result<T>> a = done.submit(wrap(first, task));
        Future<Result<T>> b = null;
        Future<Result<T>> got = delayMs > 0 ? done.poll(delayMs, TimeUnit.MILLISECONDS) : null;
        int running = 1;
        if (got == null && delayMs > 0) {
            String second = spare.get();
            Diag.mark("hedge: second circuit after " + delayMs + "ms");
            b = done.submit(wrap(second, task));
            running = 2;
        }
        Exception last = null;
        while (running > 0) {
            Future<Result<T>> f = got != null ? got : done.take();
            got = null;
            running--;
            try {
                Result<T> r = f.get();
                if (a != f) a.cancel(true);
                if (b != null && b != f) b.cancel(true);
                return r;
            } catch (ExecutionException e) {
                last = e.getCause() instanceof Exception ? (Exception) e.getCause() : e;
            }
        }
        throw last;
    }

    public static <T> Result<T> race(java.util.List<String> circuits, long staggerMs, Task<T> task) throws Exception {
        CompletionService<Result<T>> done = new ExecutorCompletionService<>(POOL);
        java.util.List<Future<Result<T>>> started = new java.util.ArrayList<>();
        started.add(done.submit(wrap(circuits.get(0), task)));
        int next = 1;
        int running = 1;
        Exception last = null;
        try {
            while (running > 0) {
                Future<Result<T>> f = next < circuits.size()
                        ? done.poll(staggerMs, TimeUnit.MILLISECONDS) : done.take();
                if (f == null) {
                    Diag.mark("race: circuit " + (next + 1) + " joins");
                    started.add(done.submit(wrap(circuits.get(next++), task)));
                    running++;
                    continue;
                }
                running--;
                try {
                    return f.get();
                } catch (ExecutionException e) {
                    last = e.getCause() instanceof Exception ? (Exception) e.getCause() : e;
                    if (next < circuits.size()) {
                        started.add(done.submit(wrap(circuits.get(next++), task)));
                        running++;
                    }
                }
            }
            throw last;
        } finally {
            for (Future<Result<T>> f : started) f.cancel(true);
        }
    }

    private static <T> Callable<Result<T>> wrap(String circuit, Task<T> task) {
        Fetch.Scope scope = Fetch.scope();
        return () -> Fetch.within(scope, () -> new Result<>(Fetch.retrying(circuit, task::run), circuit));
    }
}
