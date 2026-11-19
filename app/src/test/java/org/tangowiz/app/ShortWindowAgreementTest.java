package org.tangowiz.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class ShortWindowAgreementTest {
    @Test public void confirmsCorrectSongDespiteMiddlingFullWindow() {
        ShortWindowAgreement rule = new ShortWindowAgreement();
        assertFalse(rule.observe(1, "song", "song", .608, 60, .670, 10));
        assertFalse(rule.observe(1.5, "song", "song", .608, 60, .670, 10));
        assertTrue(rule.observe(2, "song", "song", .608, 60, .670, 10));
    }

    @Test public void disagreementAndMissingFramesBreakConfirmation() {
        ShortWindowAgreement rule = new ShortWindowAgreement();
        assertFalse(rule.observe(1, "song", "song", .608, 60, .670, 10));
        assertFalse(rule.observe(1.5, "song", "song", .608, 60, .670, 10));
        assertFalse(rule.observe(2, "song", "other", .608, 60, .670, 10));
        assertFalse(rule.observe(2.5, "song", "song", .608, 60, .670, 10));
        assertFalse(rule.observe(3, "song", "song", .70, 60, .70, 9));
        assertFalse(rule.observe(4, "song", "song", .608, 60, .670, 10));
    }
}
