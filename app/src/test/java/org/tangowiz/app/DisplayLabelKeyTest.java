package org.tangowiz.app;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import static org.junit.Assert.assertEquals;
@RunWith(Parameterized.class)
public final class DisplayLabelKeyTest {
    @Parameterized.Parameters(name="{0}") public static Collection<Object[]> cases() {
        return Arrays.asList(new Object[][] {
        {"Capitalization", "Sollozos", "SOLLOZOS", true},
        {"Leading/trailing spaces", "  Cardo azul  ", "Cardo azul", true},
        {"Repeated spaces", "No   te quiero más", "No te quiero más", true},
        {"Spaces removed", "De 6 a 7", "De6a7", true},
        {"Periods", "C. T. V.", "CTV", true},
        {"Periods and spaces", " C . T . V . ", "c t v", true},
        {"Acute accent", "María Remedios", "Maria Remedios", true},
        {"Diaeresis", "Pingüino", "Pinguino", true},
        {"Tilde", "Muñeca brava", "Muneca brava", true},
        {"Decomposed accent", "María", "María", true},
        {"Nonbreaking space", "Cardo azul", "Cardo azul", true},
        {"Tab whitespace", "Cardo\tazul", "Cardo azul", true},
        {"Combined permitted changes", "  MARÍA.   REMEDIOS ", "mariaremedios", true},
        {"Same numeric title", "Anoche a las 4", "ANOCHE A LAS 4", true},
        {"Same spelled number", "Anoche a las cuatro", "ANOCHE A LAS CUATRO", true},
        {"Same punctuation retained", "Milongueando en el '40", "MILONGUEANDO EN EL '40", true},
        {"Digit versus word", "Anoche a las 4", "Anoche a las cuatro", false},
        {"Both numbers spelled", "De 6 a 7", "De seis a siete", false},
        {"Forty word versus digits", "Milongueando en el 40", "Milongueando en el cuarenta", false},
        {"Different digits", "De 6 a 7", "De 6 a 8", false},
        {"Different spelled numbers", "Anoche a las cuatro", "Anoche a las cinco", false},
        {"Different word", "No te quiero más", "Yo te quiero más", false},
        {"Missing word", "No te quiero más", "Te quiero más", false},
        {"Different title", "Sollozos", "Suerte loca", false},
        {"Different year", "Pasional (1951)", "Pasional (1952)", false},
        {"Year annotation retained", "Pasional (1951)", "Pasional", false},
        {"Genre annotation retained", "Desde el alma (Vals 1970)", "Desde el alma", false},
        {"Orchestra prefix retained", "Orquesta Ricardo Tanturi", "Ricardo Tanturi", false},
        {"Orchestra suffix retained", "Donato Racciatti y su orquesta", "Donato Racciatti", false},
        {"Hyphen retained", "A-B", "AB", false},
        {"Apostrophe retained", "Milongueando en el '40", "Milongueando en el 40", false},
        {"Comma retained", "A, B", "AB", false},
        {"Parentheses retained", "A (B)", "AB", false},
        {"Slash retained", "A/B", "AB", false},
        {"Both empty", "", "", false},
        {"Both formatting only", " . ", "...", false},
        {"One empty", "", "Sollozos", false},
        {"Unicode digit not transliterated", "Tema ４", "Tema 4", false},
        {"Superscript not converted", "Tema ²", "Tema 2", false},
        {"Dot-like symbol retained", "A·B", "AB", false}
        });
    }
    private final String a,b; private final boolean expected;
    public DisplayLabelKeyTest(String name,String a,String b,boolean expected) {
        this.a=a;this.b=b;this.expected=expected;
    }
    @Test public void displayEquivalence() {assertEquals(expected,DisplayLabelKey.equivalent(a,b));}
}
