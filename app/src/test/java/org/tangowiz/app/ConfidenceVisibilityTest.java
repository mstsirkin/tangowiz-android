package org.tangowiz.app;
import org.junit.Test;
import static org.junit.Assert.*;
public class ConfidenceVisibilityTest {
    @Test public void belowFivePercentIsNotEligibleForPresentationOrHistory() {
        for (int percent = 0; percent < 5; percent++) assertFalse("percent="+percent, ConfidenceVisibility.visible(percent));
    }
    @Test public void fivePercentAndHigherRemainEligible() {
        assertTrue(ConfidenceVisibility.visible(5));
        assertTrue(ConfidenceVisibility.visible(100));
    }
}
