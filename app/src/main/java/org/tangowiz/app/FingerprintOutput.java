package org.tangowiz.app;

import java.nio.ByteBuffer;

/** Reads the fixed 128-float output tensor after inference. */
final class FingerprintOutput {
    static float[] read(ByteBuffer output) {
        output.rewind();
        float[] vector = new float[128];
        output.asFloatBuffer().get(vector);
        for (float value : vector) {
            if (!Float.isFinite(value)) throw new IllegalStateException("Invalid adaptive fingerprint");
        }
        return vector;
    }
}
