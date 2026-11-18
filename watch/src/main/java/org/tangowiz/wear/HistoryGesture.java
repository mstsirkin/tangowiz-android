package org.tangowiz.wear;

/** Opening history must not consume vertical scrolling, taps or standard Back. */
public final class HistoryGesture {
    private HistoryGesture() {}
    public static boolean opensHistory(float dx,float dy,float minimumDistance) {
        return Float.isFinite(dx) && Float.isFinite(dy) && Float.isFinite(minimumDistance)
                && minimumDistance>0 && dx<=-minimumDistance && -dx>Math.abs(dy)*1.5f;
    }
}
