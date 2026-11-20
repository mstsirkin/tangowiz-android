package org.tangowiz.link;

import java.util.Locale;
import java.util.StringJoiner;
import java.util.regex.Pattern;

/** Presentation rules only. These labels must never be used as recording identities. */
public final class DisplayNames {
    private static final Pattern SINGER_SEPARATOR = Pattern.compile(
            "\\s*[,;&]\\s*|\\s+y\\s+", Pattern.CASE_INSENSITIVE);

    private DisplayNames() {}

    /** Use the resource-supplied preferred label; do not guess an orchestra's surname. */
    public static String orchestra(String full, String preferred, boolean shorten) {
        if (!shorten || preferred == null || preferred.trim().isEmpty() || ambiguous(full)) return full;
        return preferred.trim();
    }

    /** A two-word credit is treated as given name + surname, not as structured catalog metadata. */
    public static String surname(String credit) {
        if (credit == null) return null;
        String name = credit.trim();
        if (ambiguous(name)) return null;
        String[] words = name.split("\\s+");
        if (words.length != 2) return null;
        String first = words[0].toLowerCase(Locale.ROOT);
        if (first.matches("de|del|di|da|das|do|dos|la|las|los|van|von|san|santa")
                || first.equals("y") || words[1].equalsIgnoreCase("y")) return null;
        // Avoid treating annotations, numbered credits or file labels as people's names.
        for (String word : words) {
            if (!word.matches("[\\p{L}\\p{M}][\\p{L}\\p{M}.'’\\-]*")) return null;
        }
        return words[1];
    }

    public static String singer(String credit, boolean firstName) {
        if (credit == null) return "";
        String full = credit.trim();
        if (full.equalsIgnoreCase("instrumental")) return "";
        String prefix = "", suffix = "";
        if (full.startsWith("? ")) { prefix = "? "; full = full.substring(2).trim(); }
        if (full.endsWith(" ?")) { suffix = " ?"; full = full.substring(0, full.length() - 2).trim(); }
        return prefix + (firstName ? full : shortSingerCredit(full)) + suffix;
    }

    private static String shortSingerCredit(String full) {
        StringJoiner names = new StringJoiner(", ");
        for (String singer : SINGER_SEPARATOR.split(full, -1)) {
            String last = surname(singer);
            if (last == null) return full;
            names.add(last);
        }
        return names.toString();
    }

    private static boolean ambiguous(String value) {
        return value == null || value.indexOf('/') >= 0 || value.indexOf('|') >= 0
                || value.indexOf('&') >= 0 || value.indexOf('+') >= 0 || value.indexOf(';') >= 0
                || value.indexOf('?') >= 0 || value.indexOf(',') >= 0;
    }
}
