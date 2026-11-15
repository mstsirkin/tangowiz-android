package org.tangowiz.app;

/** Recognition-thread policy. Times are elapsed audio seconds, never wall-clock time. */
public final class VibrationSignalPolicy {
    public enum Signal { NONE, IDENTIFIED, ENDING }
    private String candidate = "", identified = "";
    private double since = Double.NaN, last = Double.NEGATIVE_INFINITY;
    private boolean ending, endingArmed;

    public void clear() {
        candidate = identified = ""; since = Double.NaN;
        last = Double.NEGATIVE_INFINITY; ending = endingArmed = false;
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
        boolean confident = identity != null && !identity.isEmpty() && confidence >= 70 && unambiguous;
        boolean rival = confident && !identified.isEmpty() && !identity.equals(identified);
        if (rival) endingArmed = false;
        // A recently confirmed recording can end during a short confidence dip.
        // Warn on crossing its deadline, not only inside a single half-second match window.
        if (!rival && !ending && prediction != null && prediction.identity.equals(identified)
                && now >= prediction.lastConfirmedSeconds
                && now - prediction.lastConfirmedSeconds <= 15) {
            double remaining = prediction.endSeconds - now;
            if (remaining > 8) endingArmed = true;
            if (remaining <= 8 && (remaining > 7 || (endingArmed && remaining >= 6))) {
                ending = true; return Signal.ENDING;
            }
        }
        if (!confident) { candidate = ""; since = Double.NaN; return Signal.NONE; }
        if (!identity.equals(candidate)) { candidate = identity; since = now; }
        if (now - since < 2) return Signal.NONE;
        if (!identity.equals(identified)) {
            identified = identity; ending = endingArmed = false; return Signal.IDENTIFIED;
        }
        return Signal.NONE;
    }
}
