package org.tangowiz.app;

import java.util.ArrayDeque;

/** Audio-time prediction only; owned by the recognition thread. Scores are heuristic. */
public final class SongEndPredictor {
    private static final double MIN_SCORE = .60;
    private static final double SAMPLE_SECONDS = 5;
    private static final double MAX_LOOKAHEAD_SECONDS = 90;
    private static final double MAX_CONFIRMATION_AGE_SECONDS = 15;
    private final ArrayDeque<Observation> evidence = new ArrayDeque<>();
    private Prediction pending;
    private double lastSample = Double.NEGATIVE_INFINITY;
    private double lastOffset = Double.NEGATIVE_INFINITY;

    private static final class Observation {
        final double offset, end;
        final String identity;
        Observation(double offset, double end, String identity) {
            this.offset = offset; this.end = end; this.identity = identity;
        }
    }

    public static final class Prediction {
        public final String identity;
        public final double endSeconds, score, lastConfirmedSeconds, spanSeconds;
        public final int observations;
        private Prediction(String identity, double endSeconds, double score,
                           double lastConfirmedSeconds, double spanSeconds, int observations) {
            this.identity = identity; this.endSeconds = endSeconds; this.score = score;
            this.lastConfirmedSeconds = lastConfirmedSeconds; this.spanSeconds = spanSeconds;
            this.observations = observations;
        }
    }

    public void clear() {
        evidence.clear(); pending = null;
        lastSample = lastOffset = Double.NEGATIVE_INFINITY;
    }

    /** queryOffset is the start of the latest one-second fingerprint, in source audio time. */
    public void observe(double queryOffset, String identity, double score,
                        double referenceSeconds, double referenceEndSeconds, int frames, double speed) {
        if (!Double.isFinite(queryOffset)) return;
        if (queryOffset < lastOffset) clear();
        lastOffset = queryOffset;
        if (identity == null || identity.isEmpty() || !Double.isFinite(score) || score < MIN_SCORE
                || frames < 60 || !Double.isFinite(speed) || speed < .5 || speed > 2) return;
        // A confident rival invalidates an armed ending immediately, even between samples.
        if (pending != null && !pending.identity.equals(identity)) {
            pending = null; evidence.clear(); lastSample = Double.NEGATIVE_INFINITY;
        }
        if (!Double.isFinite(referenceSeconds) || !Double.isFinite(referenceEndSeconds)
                || referenceSeconds < 0 || referenceEndSeconds <= referenceSeconds) return;
        // referenceSeconds is the FIRST frame of the retained sequence, not the latest frame.
        // speed > 1 consumes source audio faster, so remaining reference time is multiplied.
        double end = queryOffset - (frames - 1) * .5 * speed
                + (referenceEndSeconds - referenceSeconds) * speed;
        if (end < queryOffset || end - queryOffset > MAX_LOOKAHEAD_SECONDS) {
            pending = null; evidence.clear(); return;
        }
        if (queryOffset - lastSample < SAMPLE_SECONDS - .001) return;
        lastSample = queryOffset;
        evidence.removeIf(o -> !o.identity.equals(identity) || queryOffset - o.offset > 35
                || Math.abs(o.end - end) > 2);
        evidence.addLast(new Observation(queryOffset, end, identity));
        double span = queryOffset - evidence.getFirst().offset;
        if (evidence.size() >= 3 && span >= 10) {
            double[] ends = evidence.stream().mapToDouble(o -> o.end).sorted().toArray();
            int middle = ends.length / 2;
            double median = ends.length % 2 == 0 ? (ends[middle - 1] + ends[middle]) / 2 : ends[middle];
            pending = new Prediction(identity, median, score, queryOffset, span, evidence.size());
        }
    }

    /** Call BEFORE searching the next fingerprint. Each armed ending can fire only once. */
    public Prediction takeReset(double nextQueryOffset) {
        if (!Double.isFinite(nextQueryOffset)) return null;
        if (nextQueryOffset < lastOffset) { clear(); return null; }
        if (pending == null) return null;
        if (nextQueryOffset - pending.lastConfirmedSeconds > MAX_CONFIRMATION_AGE_SECONDS) {
            clear(); return null;
        }
        if (nextQueryOffset < pending.endSeconds) return null;
        Prediction result = pending;
        clear();
        return result;
    }
}
