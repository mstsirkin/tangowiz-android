package org.tangowiz.link;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SingerSizingTest {
    @Test public void firstNameShrinksWithSurnameAndRemainsSmaller() {
        assertEquals(22, SingerSizing.firstNameSize(26));
        assertEquals(18, SingerSizing.firstNameSize(22));
        assertEquals(14, SingerSizing.firstNameSize(18));
        for (int size = 18; size <= 26; size++)
            assertTrue(SingerSizing.firstNameSize(size) < size);
    }
}
