package org.tangowiz.app;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public final class DisplayTitleKeyTest {
    @Test public void groupsReportedTitleVariants() {
        assertEquals(DisplayLabelKey.normalizeTitle("Adiós, Buenos Aires"),
                     DisplayLabelKey.normalizeTitle("Adiós Buenos Aires"));
        assertEquals(DisplayLabelKey.normalizeTitle("El amanecer"),
                     DisplayLabelKey.normalizeTitle("Amanecer"));
        assertEquals(DisplayLabelKey.normalizeTitle("Después de Carnaval"),
                     DisplayLabelKey.normalizeTitle("Después del Carnaval"));
    }

    @Test public void preservesWordAndNumberDifferences() {
        assertNotEquals(DisplayLabelKey.normalizeTitle("Elena"),
                        DisplayLabelKey.normalizeTitle("Ena"));
        assertNotEquals(DisplayLabelKey.normalizeTitle("Tema 1,2"),
                        DisplayLabelKey.normalizeTitle("Tema 12"));
        assertNotEquals(DisplayLabelKey.normalizeTitle("Pasional (1951)"),
                        DisplayLabelKey.normalizeTitle("Pasional (1952)"));
    }
}
