package org.tangowiz.link;

/** UI-only lifetime of a tapped full singer credit, using a monotonic clock. */
public final class SingerCreditReveal {
    public static final long DURATION_MS = 8000;
    private String credit = "";
    private long until;
    public void updateCredit(String value) {
        if (!credit.equals(value)) clear();
        credit = value;
    }
    public void toggle(long now) {
        if (visible(now)) clear();
        else if (!credit.trim().isEmpty()) until = now + DURATION_MS;
    }
    public boolean visible(long now) { return until > now; }
    public void clear() { until = 0; }
}
