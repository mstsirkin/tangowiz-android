package org.tangowiz.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class DisplayConfidenceTest {
    private double p(double score) {
        return DisplayConfidence.probability(score, DisplayConfidence.DEFAULT_A, DisplayConfidence.DEFAULT_B);
    }
    @Test public void matchesApprovedSingleScoreTable() {
        assertEquals(.0623100, p(.3), .000001);
        assertEquals(.3367414, p(.4), .000001);
        assertEquals(.7950434, p(.5), .000001);
        assertEquals(.9673604, p(.6), .000001);
        assertEquals(.9956033, p(.7), .000001);
    }
    @Test public void finiteBoundedMonotoneWithoutOutputClipping() {
        double previous = -1;
        for (int i = -100; i <= 100; i++) {
            double value = p(i / 100.);
            assertTrue(Double.isFinite(value) && value >= 0 && value < 1 && value >= previous);
            assertTrue(DisplayConfidence.percent(i / 100., DisplayConfidence.DEFAULT_A, DisplayConfidence.DEFAULT_B) < 100);
            previous = value;
        }
        assertEquals(0, DisplayConfidence.percent(Double.NaN, 1, 0));
        assertEquals(0, DisplayConfidence.percent(Double.POSITIVE_INFINITY, 1, 0));
        assertEquals(0, DisplayConfidence.probability(-1e300, 1, 0), 0);
        assertEquals(1, DisplayConfidence.probability(1e300, 1, 0), 0);
    }
    @Test public void parametersCanChangeDisplayWithoutChangingScore() {
        double raw = .5;
        assertEquals(50, DisplayConfidence.percent(raw, 2, -1));
        assertEquals(79, DisplayConfidence.percent(raw, DisplayConfidence.DEFAULT_A, DisplayConfidence.DEFAULT_B));
        assertEquals(.5, raw, 0);
        assertEquals(p(raw), DisplayConfidence.probability(raw, Double.NaN, 0), 0);
    }
}
