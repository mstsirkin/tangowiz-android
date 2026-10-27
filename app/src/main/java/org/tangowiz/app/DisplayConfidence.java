package org.tangowiz.app;

/** Experimental score-only display mapping; never use it for recognition decisions. */
public final class DisplayConfidence {
    public static final double DEFAULT_A = 20.334478026462197;
    public static final double DEFAULT_B = -8.811640746438202;
    private DisplayConfidence() {}

    public static double probability(double score, double a, double b) {
        if (!Double.isFinite(score)) return 0;
        if (!Double.isFinite(a) || a <= 0 || !Double.isFinite(b)) {
            a = DEFAULT_A; b = DEFAULT_B;
        }
        double z = a * score + b;
        if (z >= 0) return 1 / (1 + Math.exp(-z));
        double e = Math.exp(z);
        return e / (1 + e);
    }
    public static int percent(double score, double a, double b) {
        // Truncate: an estimate below 100% should not round up to certainty.
        return (int) (100 * probability(score, a, b));
    }
}
