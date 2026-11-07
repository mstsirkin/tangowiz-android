package org.tangowiz.link;

import org.junit.Test;
import static org.junit.Assert.*;

public class WatchUpdateSettingsTest {
    @Test public void defaultPreservesSeventyPercentBoundary() {
        assertEquals(5000, WatchUpdateSettings.DEFAULT_INTERVAL_MS);
        assertFalse(WatchUpdateSettings.immediate(true, 69, WatchUpdateSettings.DEFAULT_IMMEDIATE_PERCENT));
        assertTrue(WatchUpdateSettings.immediate(true, 70, WatchUpdateSettings.DEFAULT_IMMEDIATE_PERCENT));
    }
    @Test public void selectedThresholdAppliesAtItsBoundary() {
        assertFalse(WatchUpdateSettings.immediate(true, 49, 50));
        assertTrue(WatchUpdateSettings.immediate(true, 50, 50));
        assertFalse(WatchUpdateSettings.immediate(true, 89, 90));
        assertTrue(WatchUpdateSettings.immediate(true, 90, 90));
    }
    @Test public void zeroMeansAnyActualResultIncludingLowConfidence() {
        assertTrue(WatchUpdateSettings.immediate(true, 0, 0));
        assertFalse(WatchUpdateSettings.immediate(false, 0, 0));
    }
}
