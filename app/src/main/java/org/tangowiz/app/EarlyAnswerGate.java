package org.tangowiz.app;

/** Live and history gate on match length; answers from the first few fingerprints often name another recording. */
public final class EarlyAnswerGate {
    public static final int MIN_FRAMES = 7;
    public static boolean visible(int frames, boolean enabled) { return !enabled || frames >= MIN_FRAMES; }
}
