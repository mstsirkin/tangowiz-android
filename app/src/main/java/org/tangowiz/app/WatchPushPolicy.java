package org.tangowiz.app;

/** Interval gate; a newly strong result must never wait behind an earlier weak update. */
final class WatchPushPolicy {
    enum Decision { SKIP, NORMAL, NEWLY_CONFIDENT }
    private long lastScheduled;
    private String lastKey = "";
    private boolean wasConfident;

    synchronized Decision observe(long now, long interval, String key, boolean confident, boolean force) {
        boolean newlyConfident = confident && !wasConfident;
        // Observe every result, including throttled results, so a fall and recovery is detected.
        wasConfident = confident;
        if (!force && !newlyConfident && key.equals(lastKey) && now - lastScheduled < interval) {
            return Decision.SKIP;
        }
        lastKey = key;
        lastScheduled = now;
        return newlyConfident ? Decision.NEWLY_CONFIDENT : Decision.NORMAL;
    }
}
