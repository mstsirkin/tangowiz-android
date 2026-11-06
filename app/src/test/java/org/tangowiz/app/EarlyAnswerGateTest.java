package org.tangowiz.app;
import org.junit.Test;
import static org.junit.Assert.*;
public class EarlyAnswerGateTest {
    @Test public void fewerThanSevenFingerprintsAreHiddenWhenEnabled() {
        for (int frames = 0; frames < 7; frames++) assertFalse("frames="+frames, EarlyAnswerGate.visible(frames, true));
    }
    @Test public void sevenFingerprintsAndMoreAreShownWhenEnabled() {
        assertTrue(EarlyAnswerGate.visible(7, true));
        assertTrue(EarlyAnswerGate.visible(60, true));
    }
    @Test public void disabledGateShowsEveryAnswer() {
        assertTrue(EarlyAnswerGate.visible(1, false));
    }
}
