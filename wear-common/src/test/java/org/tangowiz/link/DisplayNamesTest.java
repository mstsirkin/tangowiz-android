package org.tangowiz.link;

import org.junit.Test;
import static org.junit.Assert.*;

public class DisplayNamesTest {
    @Test public void twoWordCreditsCanHideFirstNameWithoutChangingAccents() {
        assertEquals("Podestá", DisplayNames.singer("Alberto Podestá", false));
        assertEquals("Mauré", DisplayNames.singer("Héctor Mauré", false));
        assertEquals("Alberto Podestá", DisplayNames.singer("Alberto Podestá", true));
    }
    @Test public void preservesExplicitDuetsAndUnresolvedCompoundCredits() {
        for (String credit : new String[]{"Alberto del Castillo", "del Campo", "Charlo",
                "Alberto Podestá / Raúl Berón", "Dante y Martel", "Dante & Martel", "Marino, Ruiz"}) {
            assertEquals(credit, DisplayNames.singer(credit, false));
            assertNull(DisplayNames.surname(credit));
        }
    }
    @Test public void preservesUncertaintyAndDoesNotTreatAnnotationsAsNames() {
        assertEquals("? Podestá", DisplayNames.singer("? Alberto Podestá", false));
        assertEquals("Podestá ?", DisplayNames.singer("Alberto Podestá ?", false));
        assertEquals("Unknown singer?", DisplayNames.singer("Unknown singer?", false));
        assertEquals("Singer 1940", DisplayNames.singer("Singer 1940", false));
        assertEquals("", DisplayNames.singer(null, false));
    }
    @Test public void acceptsUnicodeApostrophesHyphensAndWhitespace() {
        assertEquals("O’Connor", DisplayNames.singer("Sean O’Connor", false));
        assertEquals("Díaz-Soto", DisplayNames.singer("Juan  Díaz-Soto", false));
        assertEquals("Pode\u0301sta", DisplayNames.singer("Alberto Pode\u0301sta", false));
    }
    @Test public void orchestraUsesOnlyTheSuppliedPreferredLabel() {
        assertEquals("Di Sarli", DisplayNames.orchestra("Carlos Di Sarli", "Di Sarli", true));
        assertEquals("LJD", DisplayNames.orchestra("La Juan D’Arienzo", "LJD", true));
        assertEquals("Carlos Di Sarli", DisplayNames.orchestra("Carlos Di Sarli", "Di Sarli", false));
        assertEquals("Unknown ensemble", DisplayNames.orchestra("Unknown ensemble", null, true));
        assertEquals("Caló / Tanturi ?", DisplayNames.orchestra("Caló / Tanturi ?", "Caló", true));
    }
}
