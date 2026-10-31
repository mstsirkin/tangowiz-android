package org.tangowiz.app;
final class FallbackTiming {
    static double delay(double intervalSeconds, boolean busy) {
        return busy ? 0.5 : intervalSeconds;
    }
}
