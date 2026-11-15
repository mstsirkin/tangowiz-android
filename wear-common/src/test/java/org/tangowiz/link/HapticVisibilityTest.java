package org.tangowiz.link;
import org.junit.Test;
import static org.junit.Assert.*;
public class HapticVisibilityTest {
 @Test public void onlyStartedAppActivitiesPermitHaptics(){HapticVisibility v=new HapticVisibility();Object a=new Object();assertFalse(v.visible());v.started(a);assertTrue(v.visible());/* Ambient pause does not stop the visible activity. */assertTrue(v.visible());v.stopped(a);assertFalse(v.visible());}
 @Test public void overlappingActivitiesAndDuplicateCallbacksAreSafe(){HapticVisibility v=new HapticVisibility();Object a=new Object(),b=new Object();v.started(a);v.started(a);v.started(b);v.stopped(a);assertTrue(v.visible());v.stopped(a);v.stopped(b);assertFalse(v.visible());}
}
