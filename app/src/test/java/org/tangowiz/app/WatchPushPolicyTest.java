package org.tangowiz.app;

import org.junit.Test;
import static org.junit.Assert.*;
import static org.tangowiz.app.WatchPushPolicy.Decision.*;

public class WatchPushPolicyTest {
    @Test public void confidenceRiseBypassesFiveSecondInterval() {
        WatchPushPolicy gate = new WatchPushPolicy();
        assertEquals(NORMAL, gate.observe(10000, 5000, "listening", false, false));
        assertEquals(SKIP, gate.observe(10100, 5000, "listening", false, false));
        assertEquals(NEWLY_CONFIDENT, gate.observe(10200, 5000, "listening", true, false));
        assertEquals(SKIP, gate.observe(10300, 5000, "listening", true, false));
        assertEquals(NORMAL, gate.observe(15200, 5000, "listening", true, false));
    }
    @Test public void throttledConfidenceFallStillAllowsImmediateRecovery() {
        WatchPushPolicy gate = new WatchPushPolicy();
        assertEquals(NEWLY_CONFIDENT, gate.observe(10000, 60000, "listening", true, false));
        assertEquals(SKIP, gate.observe(10100, 60000, "listening", false, false));
        assertEquals(NEWLY_CONFIDENT, gate.observe(10200, 60000, "listening", true, false));
    }
    @Test public void controlChangesAndExplicitRequestsStillBypassInterval() {
        WatchPushPolicy gate = new WatchPushPolicy();
        assertEquals(NORMAL, gate.observe(10000, 5000, "paused", false, false));
        assertEquals(NORMAL, gate.observe(10100, 5000, "listening", false, false));
        assertEquals(NORMAL, gate.observe(10200, 5000, "listening", false, true));
        assertEquals(NEWLY_CONFIDENT, gate.observe(10300, 5000, "listening", true, true));
    }
}
