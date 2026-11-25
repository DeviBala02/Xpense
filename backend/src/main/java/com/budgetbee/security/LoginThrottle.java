package com.budgetbee.security;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Locks a login name for a while after too many wrong passwords. Time is passed in, so it is easy to test. */
public class LoginThrottle {

    private static final class State {
        int fails;
        long lockedUntil;
    }

    private final int maxFails;
    private final long lockMillis;
    private final Map<String, State> states = new ConcurrentHashMap<>();

    public LoginThrottle(int maxFails, long lockMillis) {
        this.maxFails = maxFails;
        this.lockMillis = lockMillis;
    }

    public synchronized boolean isLocked(String key, long nowMillis) {
        State s = states.get(key);
        return s != null && s.lockedUntil > nowMillis;
    }

    public synchronized long secondsLeft(String key, long nowMillis) {
        State s = states.get(key);
        if (s == null || s.lockedUntil <= nowMillis) return 0;
        return (s.lockedUntil - nowMillis + 999) / 1000;
    }

    public synchronized void fail(String key, long nowMillis) {
        State s = states.computeIfAbsent(key, k -> new State());
        if (s.lockedUntil > 0 && s.lockedUntil <= nowMillis) {   // an old lock ended: start counting again
            s.fails = 0;
            s.lockedUntil = 0;
        }
        s.fails++;
        if (s.fails >= maxFails) s.lockedUntil = nowMillis + lockMillis;
    }

    public synchronized void success(String key) {
        states.remove(key);
    }
}
