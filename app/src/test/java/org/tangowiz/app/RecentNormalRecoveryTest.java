package org.tangowiz.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class RecentNormalRecoveryTest {
    private boolean observe(RecentNormalRecovery p, double t, String id, double score, double ref) {
        return p.observe(t, id, score, .20, ref, 10, 1.0);
    }

    @Test public void unAmorRecoversAfterThreeAdvancingStrongChecks() {
        // Saved phone: session 21-33-38, audio 22.5/23.5/24.5s; reference starts 0/1/2s.
        RecentNormalRecovery p = new RecentNormalRecovery();
        assertFalse(observe(p, 21, "youtube4-RVkX86zmdFo", .705141, 0));
        assertFalse(observe(p, 22, "youtube4-RVkX86zmdFo", .705348, 1));
        assertTrue("Strong recent Un amor must recover behind stale long context",
                observe(p, 23, "youtube4-RVkX86zmdFo", .707303, 2));
    }

    @Test public void weakOrInsufficientImprovementCannotRecover() {
        RecentNormalRecovery p = new RecentNormalRecovery();
        for (int i=0;i<8;i++) assertFalse(observe(p, i, "weak", .649, i));
        for (int i=0;i<8;i++) assertFalse(p.observe(i, "rival", .70, .63, i, 10, 1));
        for (int i=0;i<8;i++) assertFalse(p.observe(i, "short", .90, .20, i, 9, 1));
    }

    @Test public void changingSourcesAndStationaryReferenceCannotAccumulateEvidence() {
        RecentNormalRecovery p = new RecentNormalRecovery();
        for (int i=0;i<8;i++) assertFalse(observe(p, i, i%2==0?"a":"b", .8, i));
        p.clear();
        for (int i=0;i<8;i++) assertFalse(observe(p, i, "a", .8, 10));
    }

    @Test public void staleObservationsAndGenerationClearDoNotCarrySupport() {
        RecentNormalRecovery p = new RecentNormalRecovery();
        assertFalse(observe(p, 0, "a", .8, 0));
        assertFalse(observe(p, 1, "a", .8, 1));
        p.clear();
        assertFalse(observe(p, 2, "a", .8, 2));
        assertFalse(observe(p, 10, "a", .8, 10));
        assertFalse(observe(p, 11, "a", .8, 11));
        assertTrue(observe(p, 12, "a", .8, 12));
    }

    @Test public void rejectedCheckBreaksTheConfirmationRun() {
        RecentNormalRecovery p = new RecentNormalRecovery();
        assertFalse(observe(p, 0, "a", .8, 0));
        assertFalse(observe(p, 1, "a", .8, 1));
        assertFalse(observe(p, 2, "a", .50, 2));
        assertFalse(observe(p, 3, "a", .8, 3));
        assertFalse(observe(p, 4, "a", .8, 4));
        assertTrue(observe(p, 5, "a", .8, 5));
    }

    @Test public void duplicateTimesCannotSupplyFreshConfirmations() {
        RecentNormalRecovery p = new RecentNormalRecovery();
        assertFalse(observe(p, 0, "a", .8, 0));
        for (int i=0;i<8;i++) assertFalse(observe(p, 0, "a", .8, 0));
        assertFalse(observe(p, 1, "a", .8, 1));
        assertTrue(observe(p, 2, "a", .8, 2));
    }

    @Test public void invalidEvidenceCannotRecover() {
        RecentNormalRecovery p = new RecentNormalRecovery();
        for (int i=0;i<4;i++) {
            assertFalse(p.observe(i, "", .8, .2, i, 10, 1));
            assertFalse(p.observe(i, "a", Double.NaN, .2, i, 10, 1));
            assertFalse(p.observe(i, "a", .8, .2, Double.NaN, 10, 1));
            assertFalse(p.observe(i, "a", .8, .2, i, 10, 1.005));
        }
    }

    @Test public void checksAreBoundedAndSuppressedWithoutWeakMatureAudio() {
        RecentNormalRecovery p = new RecentNormalRecovery();
        assertFalse(p.shouldCheck(20, .2, 19, true));
        assertFalse(p.shouldCheck(20, .7, 60, true));
        assertFalse(p.shouldCheck(20, .2, 60, false));
        assertTrue(p.shouldCheck(20, .2, 60, true));
        assertFalse(p.shouldCheck(20.5, .2, 60, true));
        assertTrue(p.shouldCheck(21, .2, 60, true));
    }
}
