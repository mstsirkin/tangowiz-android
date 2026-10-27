package org.tangowiz.app;

/** Capture-side look-back; snapshots never share mutable microphone buffers. */
final class RecentAudioWindow {
    private final short[] ring;
    private int size, next;
    RecentAudioWindow(int capacity) { if (capacity <= 0) throw new IllegalArgumentException(); ring = new short[capacity]; }
    synchronized void append(short[] samples) {
        for (short sample : samples) { ring[next] = sample; next = (next + 1) % ring.length; if (size < ring.length) size++; }
    }
    synchronized short[] snapshot() {
        short[] result = new short[size];
        int start = (next - size + ring.length) % ring.length;
        for (int i = 0; i < size; i++) result[i] = ring[(start + i) % ring.length];
        return result;
    }
}
