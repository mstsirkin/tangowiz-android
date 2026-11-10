package org.tangowiz.link;

/** Sizes in scaled pixels for the first name when highlighting a surname. */
public final class SingerSizing {
    private SingerSizing() {}
    public static int firstNameSize(int surnameSize) { return surnameSize - 4; }
}
