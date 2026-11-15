package org.tangowiz.link;

import org.junit.Test;
import static org.junit.Assert.*;

public final class VibrationSettingsTest {
    @Test public void independentSwitchesCoverEveryCombination() {
        for (boolean identification : new boolean[]{false,true})
            for (boolean ending : new boolean[]{false,true}) {
                assertEquals(identification,VibrationSettings.enabled(1,identification,ending));
                assertEquals(ending,VibrationSettings.enabled(2,identification,ending));
                assertFalse(VibrationSettings.enabled(0,identification,ending));
                assertFalse(VibrationSettings.enabled(3,identification,ending));
            }
    }
    @Test public void inheritedSettingOnlyEnablesIdentification() {
        for (boolean previous : new boolean[]{false,true}) {
            assertEquals(previous,VibrationSettings.enabled(1,previous,false));
            assertFalse(VibrationSettings.enabled(2,previous,false));
        }
    }
    @Test public void phoneAndWatchSettingsAreIndependent() {
        assertTrue(VibrationSettings.enabled(1,true,false)); // phone
        assertFalse(VibrationSettings.enabled(1,false,true)); // watch
        assertFalse(VibrationSettings.enabled(2,true,false)); // phone
        assertTrue(VibrationSettings.enabled(2,false,true)); // watch
    }
}
