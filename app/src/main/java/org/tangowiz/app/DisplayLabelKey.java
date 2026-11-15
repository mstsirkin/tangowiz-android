package org.tangowiz.app;

import java.text.Normalizer;
import java.util.Locale;

/** Display equivalence only; words, numbers and other punctuation remain intact. */
public final class DisplayLabelKey {
    private DisplayLabelKey() {}
    public static String normalize(String value) {
        String text = Normalizer.normalize(value, Normalizer.Form.NFD).toLowerCase(Locale.ROOT);
        StringBuilder key = new StringBuilder();
        text.codePoints().forEach(c -> {
            int type = Character.getType(c);
            if (c != '.' && !Character.isWhitespace(c) && !Character.isSpaceChar(c)
                    && type != Character.NON_SPACING_MARK && type != Character.COMBINING_SPACING_MARK
                    && type != Character.ENCLOSING_MARK) key.appendCodePoint(c);
        });
        return key.toString();
    }
    public static boolean equivalent(String a, String b) {
        String key = normalize(a);
        return !key.isEmpty() && key.equals(normalize(b));
    }
}
