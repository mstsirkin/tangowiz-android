package org.tangowiz.link;

import java.util.Locale;

/** Presentation rules only. These labels must never be used as recording identities. */
public final class DisplayNames {
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
        String prefix = "", suffix = "";
        if (full.startsWith("? ")) { prefix = "? "; full = full.substring(2).trim(); }
        if (full.endsWith(" ?")) { suffix = " ?"; full = full.substring(0, full.length() - 2).trim(); }
        String last = firstName ? null : surname(full);
        return prefix + (last == null ? full : last) + suffix;
    }

    private static boolean ambiguous(String value) {
        return value == null || value.indexOf('/') >= 0 || value.indexOf('|') >= 0
                || value.indexOf('&') >= 0 || value.indexOf('+') >= 0 || value.indexOf(';') >= 0
                || value.indexOf('?') >= 0 || value.indexOf(',') >= 0;
    }
}
