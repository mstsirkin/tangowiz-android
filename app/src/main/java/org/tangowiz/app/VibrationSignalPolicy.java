package org.tangowiz.app;

/** Recognition-thread policy. Times are elapsed audio seconds, never wall-clock time. */
public final class VibrationSignalPolicy {
    public enum Signal { NONE, IDENTIFIED, ENDING }
    private String candidate = "", identified = "";
    private double since = Double.NaN, last = Double.NEGATIVE_INFINITY;
    private boolean ending;

    public void clear() {
        candidate = identified = ""; since = Double.NaN;
        last = Double.NEGATIVE_INFINITY; ending = false;
    }

    public Signal observe(double now, String identity, int confidence, boolean unambiguous,
                          SongEndPredictor.Prediction prediction, boolean timely) {
        if (!timely) return Signal.NONE;
        return observe(now, identity, confidence, unambiguous, prediction);
    }

    public Signal observe(double now, String identity, int confidence, boolean unambiguous,
                          SongEndPredictor.Prediction prediction) {
        if (!Double.isFinite(now)) return Signal.NONE;
        if (now < last) clear();
        last = now;
        if (identity == null || identity.isEmpty() || confidence < 70 || !unambiguous) {
            candidate = ""; since = Double.NaN; return Signal.NONE;
        }
        if (!identity.equals(candidate)) { candidate = identity; since = now; }
        // A short stable run avoids buzzing for a single transient high score.
        if (now - since < 2) return Signal.NONE;
        if (!identity.equals(identified)) {
            identified = identity; ending = false; return Signal.IDENTIFIED;
        }
        if (!ending && prediction != null && identity.equals(prediction.identity)
                && now >= prediction.lastConfirmedSeconds
                && now - prediction.lastConfirmedSeconds <= 15) {
            double remaining = prediction.endSeconds - now;
            // Do not deliver a late catch-up warning if recognition only settles at the ending.
            if (remaining > 7 && remaining <= 8) { ending = true; return Signal.ENDING; }
        }
        return Signal.NONE;
    }
}
