package org.tangowiz.link;

/** Shared defaults and confidence rule for phone pushes and the watch display. */
public final class WatchUpdateSettings {
    public static final long DEFAULT_INTERVAL_MS = 5000;
    public static final int DEFAULT_IMMEDIATE_PERCENT = 70;
    private WatchUpdateSettings() {}

    public static boolean immediate(boolean hasResult, int percent, int threshold) {
        return hasResult && percent >= Math.max(0, Math.min(100, threshold));
    }
}
