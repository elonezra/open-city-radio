package com.opencity.radio;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;

/** Pure JVM clock, shared by the Kotlin service and executable core tests. */
public final class LiveClock {
    private LiveClock() {}
    private static long add(long a, long b, long duration) {
        return a >= duration - b ? a - (duration - b) : a + b;
    }
    public static long position(long nowMs, long durationMs, String stationId,
                                long offsetMs, boolean dailySeed, long epochMs) {
        if (durationMs <= 0) throw new IllegalArgumentException("Audio duration must be positive");
        long seed = 0;
        if (dailySeed) {
            String day = Instant.ofEpochMilli(nowMs).atZone(ZoneOffset.UTC).toLocalDate().toString();
            try {
                byte[] hash = MessageDigest.getInstance("SHA-256").digest((stationId + ":" + day).getBytes(StandardCharsets.UTF_8));
                for (int i = 0; i < 7; i++) seed = (seed << 8) | (hash[i] & 255L);
                seed %= durationMs;
            } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        }
        long epochInverse = Math.floorMod(-Math.floorMod(epochMs, durationMs), durationMs);
        long base = add(Math.floorMod(nowMs, durationMs), epochInverse, durationMs);
        return add(add(base, Math.floorMod(offsetMs, durationMs), durationMs), seed, durationMs);
    }
}
