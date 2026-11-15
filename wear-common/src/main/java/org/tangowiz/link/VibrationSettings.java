package org.tangowiz.link;

/** Independent identification (one tick) and ending (two ticks) opt-ins. */
public final class VibrationSettings {
    private VibrationSettings() {}
    public static boolean enabled(int pulses, boolean identification, boolean ending) {
        return pulses == 1 ? identification : pulses == 2 && ending;
    }
}
