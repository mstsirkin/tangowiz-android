package org.tangowiz.link;

import org.junit.Test;
import static org.junit.Assert.*;

public class SingerCreditRevealTest {
    @Test public void fullCreditCannotRemainOpenIndefinitely() {
        SingerCreditReveal state = new SingerCreditReveal();
        state.updateCredit("Alberto Echagüe");
        state.toggle(100);
        assertTrue(state.visible(100));
        assertTrue(state.visible(100 + SingerCreditReveal.DURATION_MS - 1));
        assertFalse("Singer details must return to the normal display automatically",
                state.visible(100 + SingerCreditReveal.DURATION_MS));
    }
    @Test public void changedSingerOrLeavingTheViewClearsTheReveal() {
        SingerCreditReveal state = new SingerCreditReveal();
        state.updateCredit("Alberto Echagüe"); state.toggle(100);
        state.updateCredit("Armando Laborde"); assertFalse(state.visible(200));
        state.toggle(300); state.clear(); assertFalse(state.visible(301));
    }
    @Test public void secondTapClosesAndEmptyCreditDoesNotOpen() {
        SingerCreditReveal state = new SingerCreditReveal();
        state.toggle(100); assertFalse(state.visible(100));
        state.updateCredit("Alberto Echagüe"); state.toggle(200);
        state.toggle(201); assertFalse(state.visible(201));
    }
}
