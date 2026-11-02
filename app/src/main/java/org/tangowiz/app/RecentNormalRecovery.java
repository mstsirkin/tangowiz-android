package org.tangowiz.app;

/** Temporal evidence for recovering the normal stream from older unmatched context. */
public final class RecentNormalRecovery {
    public static final int RECENT_FRAMES = 10;
    public static final double CHECK_INTERVAL_SECONDS = 1;
    private double lastCheck = Double.NEGATIVE_INFINITY;
    private String identity = "";
    private int observations;
    private double firstOffset, lastOffset, lastReference;

    private void clearEvidence() { identity = ""; observations = 0; }
    public void clear() { lastCheck = Double.NEGATIVE_INFINITY; clearEvidence(); }

    public boolean shouldCheck(double queryOffset, double currentScore, int normalFrames, boolean audible) {
        if (!Double.isFinite(queryOffset) || !Double.isFinite(currentScore)
                || !audible || currentScore >= .60 || normalFrames < 20) {
            clearEvidence(); return false;
        }
        if (queryOffset < lastCheck) clear();
        if (queryOffset - lastCheck < CHECK_INTERVAL_SECONDS - .001) return false;
        lastCheck = queryOffset;
        return true;
    }

    public boolean observe(double queryOffset, String sourceId, double score,
                           double currentScore, double referenceSeconds, int frames, double speed) {
        if (!Double.isFinite(queryOffset) || sourceId == null || sourceId.isEmpty()
                || !Double.isFinite(score) || !Double.isFinite(currentScore)
                || !Double.isFinite(referenceSeconds) || referenceSeconds < 0
                || !Double.isFinite(speed) || Math.abs(speed - 1) > .000001
                || frames != RECENT_FRAMES || score < .65 || score < currentScore + .08) {
            clearEvidence(); return false;
        }
        if (observations > 0 && queryOffset >= lastOffset && queryOffset - lastOffset < .999)
            return false; // Repeated/overlapping observations cannot supply fresh support.
        double delta = queryOffset - lastOffset;
        boolean consistent = observations > 0 && identity.equals(sourceId)
                && delta >= .999 && delta <= 1.501
                && Math.abs(referenceSeconds - lastReference - delta) <= .75;
        if (!consistent) {
            identity = sourceId; observations = 1; firstOffset = queryOffset;
        } else observations++;
        lastOffset = queryOffset; lastReference = referenceSeconds;
        boolean confirmed = observations >= 3 && queryOffset - firstOffset >= 1.999;
        if (confirmed) clearEvidence();
        return confirmed;
    }
}
