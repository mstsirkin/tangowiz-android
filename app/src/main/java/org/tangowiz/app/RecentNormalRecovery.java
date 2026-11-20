package org.tangowiz.app;

/** Temporal evidence for recovering the normal stream from older unmatched context. */
public final class RecentNormalRecovery {
    public static final int RECENT_FRAMES = 10;
    public static final double CHECK_INTERVAL_SECONDS = 1;
    private double lastCheck = Double.NEGATIVE_INFINITY;
    private String identity = "";
    private int observations;
    private double firstOffset, lastOffset, lastReference;

    private final java.util.Map<String, RecentNormalRecovery> copies = new java.util.HashMap<>();
    private String candidateGroup = "";
    private void clearEvidence() { identity = ""; observations = 0; copies.clear(); candidateGroup = ""; }
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
    /** Only scored copies in the same recording group may contribute evidence.
     * Each copy retains its own reference clock; group labels never align offsets. */
    public boolean observeCandidates(double t, String group, String[] ids, double[] scores,
                                     double current, double[] refs, int frames, double speed) {
        if(group==null || group.isEmpty() || ids==null || scores==null || refs==null
                || ids.length==0 || ids.length>256 || ids.length!=scores.length || ids.length!=refs.length) {
            clearEvidence(); return false;
        }
        java.util.Set<String> seen=new java.util.HashSet<>();
        for(String id:ids) if(id==null || id.isEmpty() || !seen.add(id)) { clearEvidence(); return false; }
        if(!candidateGroup.equals(group)) { clearEvidence(); candidateGroup=group; }
        copies.keySet().retainAll(seen);
        boolean confirmed=false;
        for(int i=0;i<ids.length;i++) {
            RecentNormalRecovery copy=copies.computeIfAbsent(ids[i], key->new RecentNormalRecovery());
            confirmed |= copy.observe(t,ids[i],scores[i],current,refs[i],frames,speed);
        }
        if(confirmed) clearEvidence();
        return confirmed;
    }
}
