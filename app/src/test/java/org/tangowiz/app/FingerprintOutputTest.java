package org.tangowiz.app;

import org.junit.Test;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import static org.junit.Assert.*;

public class FingerprintOutputTest {
    @Test public void readsTensorWhenInferenceLeavesPositionAtEnd() {
        ByteBuffer output = ByteBuffer.allocateDirect(128 * Float.BYTES).order(ByteOrder.nativeOrder());
        float[] expected = new float[128];
        for (int i = 0; i < expected.length; i++) {
            expected[i] = (i - 64) / 128f;
            output.putFloat(expected[i]);
        }
        assertEquals(output.limit(), output.position());
        assertArrayEquals(expected, FingerprintOutput.read(output), 0f);
    }
}
