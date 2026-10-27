package org.tangowiz.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class SongEndPredictorTest {
    private void observe(SongEndPredictor p, double t, double end, String id, double speed) {
        double reference = 150 - (end - t) / speed - 29.5;
        p.observe(t, id, .8, reference, 150, 60, speed);
    }
    private SongEndPredictor armed() {
        SongEndPredictor p = new SongEndPredictor();
        for (double t : new double[]{80, 85, 90}) observe(p, t, 100, "song", 1);
        return p;
    }
    @Test public void resetsOnceAtPredictedBoundary() {
        SongEndPredictor p = armed();
        assertNull(p.takeReset(99.5));
        SongEndPredictor.Prediction reset = p.takeReset(100);
        assertNotNull(reset); assertEquals("song", reset.identity);
        assertEquals(100, reset.endSeconds, 1e-8);
        assertEquals(3, reset.observations); assertEquals(10, reset.spanSeconds, 0);
        assertNull(p.takeReset(100.5));
    }
    @Test public void accountsForSequenceStartAndBothSpeedDirections() {
        for (double speed : new double[]{.97, 1.03}) {
            SongEndPredictor p = new SongEndPredictor();
            for (double t : new double[]{80, 85, 90}) observe(p, t, 100, "song", speed);
            assertNull(p.takeReset(99));
            assertNotNull(p.takeReset(100.001));
        }
    }
    @Test public void frequentWindowsStillRequireTenSecondsOfEvidence() {
        SongEndPredictor p = new SongEndPredictor();
        for (double t = 80; t < 90; t += .5) observe(p, t, 100, "song", 1);
        assertNull(p.takeReset(100));
    }
    @Test public void rejectsWeakShortAndMalformedEvidence() {
        for (int kind = 0; kind < 5; kind++) {
            SongEndPredictor p = new SongEndPredictor();
            for (double t : new double[]{80, 85, 90}) {
                p.observe(t, "song", kind == 0 ? .59 : .8,
                        kind == 1 ? Double.NaN : t + 20.5,
                        kind == 2 ? Double.NaN : 150,
                        kind == 3 ? 10 : 60, kind == 4 ? Double.NaN : 1);
            }
            assertNull(p.takeReset(100));
        }
    }
    @Test public void inconsistentEndEstimatesDoNotArm() {
        SongEndPredictor p = new SongEndPredictor();
        observe(p, 80, 100, "song", 1); observe(p, 85, 104, "song", 1);
        observe(p, 90, 100, "song", 1);
        assertNull(p.takeReset(100));
    }
    @Test public void confidentRivalCancelsEvenBetweenSamples() {
        SongEndPredictor p = armed();
        observe(p, 90.5, 110, "rival", 1);
        assertNull(p.takeReset(100));
    }
    @Test public void briefWeakEndingDoesNotEraseSupportedBoundary() {
        SongEndPredictor p = armed();
        p.observe(95, "incidental-rival", .2, 0, 150, 60, 1);
        assertNotNull(p.takeReset(100));
    }
    @Test public void stalePredictionDoesNotFireAfterLongLossOfSupport() {
        SongEndPredictor p = new SongEndPredictor();
        for (double t : new double[]{70, 75, 80}) observe(p, t, 100, "song", 1);
        assertNull(p.takeReset(100));
    }
    @Test public void clearingAndRestartingAudioTimeDiscardPrediction() {
        SongEndPredictor p = armed(); p.clear(); assertNull(p.takeReset(100));
        p = armed(); assertNull(p.takeReset(0)); assertNull(p.takeReset(100));
    }
    @Test public void doesNotPredictFarAwayOrAlreadyFinishedReference() {
        for (double end : new double[]{70, 200}) {
            SongEndPredictor p = new SongEndPredictor();
            for (double t : new double[]{80, 85, 90}) observe(p, t, end, "song", 1);
            assertNull(p.takeReset(100));
        }
    }
}
