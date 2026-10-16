package com.orvid.image;

import java.util.HashMap;
import java.util.Map;

final class CircuitHealth {

    static final long SLOW_MS = 4000;
    static final int STRIKES = 3;
    static final long COOLDOWN_MS = 30000;

    private static final class State {
        int strikes;
        long rotatedAt = Long.MIN_VALUE / 2;
    }

    private final Map<String, State> states = new HashMap<>();

    synchronized boolean record(String circuit, long elapsedMs, boolean ok, long now) {
        State s = states.computeIfAbsent(circuit, k -> new State());
        if (ok && elapsedMs < SLOW_MS) {
            s.strikes = 0;
            return false;
        }
        if (now - s.rotatedAt < COOLDOWN_MS) return false;
        if (++s.strikes < STRIKES) return false;
        s.strikes = 0;
        s.rotatedAt = now;
        return true;
    }
}
