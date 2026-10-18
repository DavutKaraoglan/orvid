package com.orvid.net;

import java.util.concurrent.Callable;

public final class Trace {

    public enum Step { TOR, LANE, RETRY, AGAIN }

    public interface Sink {
        void note(Step step, int n);

        default void page(String detail) {
        }
    }

    private static final ThreadLocal<Sink> SINK = new ThreadLocal<>();

    private Trace() {
    }

    public static Sink current() {
        return SINK.get();
    }

    public static <T> T with(Sink sink, Callable<T> work) throws Exception {
        Sink previous = SINK.get();
        SINK.set(sink);
        try {
            return work.call();
        } finally {
            if (previous == null) SINK.remove();
            else SINK.set(previous);
        }
    }

    public static void page(String detail) {
        Sink s = SINK.get();
        if (s != null) s.page(detail);
    }

    public static void note(Step step, int n) {
        Sink s = SINK.get();
        if (s != null) s.note(step, n);
    }
}
