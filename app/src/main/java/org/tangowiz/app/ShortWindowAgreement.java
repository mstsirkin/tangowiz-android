package org.tangowiz.app;

/** Confirms agreement between the normal-speed full and recent windows. */
public final class ShortWindowAgreement {
    private String group = "";
    private double lastOffset = Double.NaN;
    private int consecutive;

    public void clear() {
        group = "";
        lastOffset = Double.NaN;
        consecutive = 0;
    }

    public static boolean strong(double score, int frames) {
        return Double.isFinite(score) && score >= .65 && frames >= 10;
    }

    public boolean observe(double offset, String fullGroup, String recentGroup,
                           double fullScore, int fullFrames, double recentScore, int recentFrames) {
        if (!Double.isFinite(offset) || fullGroup == null || fullGroup.isEmpty()
                || !fullGroup.equals(recentGroup) || recentFrames != 10
                || !(strong(fullScore, fullFrames) || strong(recentScore, recentFrames))) {
            clear();
            return false;
        }
        double delta = offset - lastOffset;
        consecutive = group.equals(fullGroup) && Math.abs(delta - .5) <= .01
                ? Math.min(consecutive + 1, 3) : 1;
        group = fullGroup;
        lastOffset = offset;
        return consecutive >= 3;
    }
}
