package org.tangowiz.app;
import org.junit.Test;
import static org.junit.Assert.*;
public class FallbackTimingTest {
    @Test public void successfulLaunchRestartsConfiguredInterval() {
        assertEquals(2.0, FallbackTiming.delay(2.0,false),0);
        assertEquals(3.0, FallbackTiming.delay(3.0,false),0);
    }
    @Test public void busyRetriesEveryHalfSecondInsteadOfWaitingFullInterval() {
        double next=2.0;
        next+=FallbackTiming.delay(2.0,true);
        assertEquals(2.5,next,0);
        next+=FallbackTiming.delay(2.0,true);
        assertEquals(3.0,next,0);
    }
}
