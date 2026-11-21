package org.tangowiz.app;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Display equivalence only; words, numbers and other punctuation remain intact. */
public final class DisplayLabelKey {
    private static final Pattern FORMATTING_COMMA = Pattern.compile("(?<!\\p{N}),|,(?!\\p{N})");
    private static final Pattern LEADING_EL = Pattern.compile("^[\\s\\p{Z}]*el[\\s\\p{Z}]+(?=\\p{L})");
    private static final Pattern DE_ARTICLE = Pattern.compile("(?<![\\p{L}\\p{N}])(?:del|de[\\s\\p{Z}]+el)(?=[\\s\\p{Z}]+\\p{L})");

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
    public static String normalizeTitle(String value) {
        String text = FORMATTING_COMMA.matcher(value.toLowerCase(Locale.ROOT)).replaceAll(" ");
        text = LEADING_EL.matcher(text).replaceFirst("");
        return normalize(DE_ARTICLE.matcher(text).replaceAll("de"));
    }
    public static boolean equivalent(String a, String b) {
        String key = normalize(a);
        return !key.isEmpty() && key.equals(normalize(b));
    }
}
