package org.tangowiz.link;

import org.junit.Test;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.UUID;
import static org.junit.Assert.*;

public class AudioWireTest {
    private static final String ID = "13ee4731-9fa2-459c-9977-3a4ce439c451";
    private static byte[] header() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        AudioWire.writeHeader(out, ID);
        return out.toByteArray();
    }
    private static short[] samples() {
        short[] pcm = new short[AudioWire.FRAME_SAMPLES];
        for (int i = 0; i < pcm.length; i++) pcm[i] = (short) (i * 29 - 32768);
        pcm[0] = 0x1234; pcm[1] = -32768; pcm[2] = 32767; pcm[3] = -1;
        return pcm;
    }
    private static byte[] frame(long sequence) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        AudioWire.writeFrame(out, sequence, samples());
        return out.toByteArray();
    }
    private static void badHeader(byte[] bytes) {
        assertThrows(IOException.class, () -> AudioWire.readHeader(new ByteArrayInputStream(bytes)));
    }
    private static void badFrame(byte[] bytes, long expected) {
        assertThrows(IOException.class, () -> AudioWire.readFrame(new ByteArrayInputStream(bytes), expected));
    }
    @Test public void completeStreamRoundTripAndEndianContract() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        AudioWire.writeHeader(out, ID);
        AudioWire.writeFrame(out, 0, samples());
        AudioWire.writeFrame(out, 1, samples());
        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        assertEquals(ID, AudioWire.readHeader(in));
        assertArrayEquals(samples(), AudioWire.readFrame(in, 0));
        assertArrayEquals(samples(), AudioWire.readFrame(in, 1));
        assertThrows(EOFException.class, () -> AudioWire.readFrame(in, 2));
        byte[] bytes = frame(2);
        assertEquals(AudioWire.FRAME_BYTES, bytes.length);
        assertEquals(2, ByteBuffer.wrap(bytes).getLong(8));
        assertEquals(8000, ByteBuffer.wrap(bytes).getLong(16));
        assertEquals(0x34, bytes[24] & 255); assertEquals(0x12, bytes[25] & 255);
        assertEquals(0, bytes[26] & 255); assertEquals(128, bytes[27] & 255);
    }
    @Test public void everyTruncatedHeaderFailsExplicitly() throws IOException {
        byte[] bytes = header();
        assertEquals(AudioWire.HEADER_BYTES, bytes.length);
        for (int length = 0; length < bytes.length; length++) {
            final byte[] truncated = Arrays.copyOf(bytes, length);
            assertThrows(EOFException.class, () -> AudioWire.readHeader(new ByteArrayInputStream(truncated)));
        }
    }
    @Test public void truncatedFramesFailAtFramingAndPayloadBoundaries() throws IOException {
        byte[] bytes = frame(0);
        for (int length : new int[]{0, 1, 4, 7, 8, 15, 16, 23, 24, 25, 27, 4000, bytes.length - 1}) {
            final byte[] truncated = Arrays.copyOf(bytes, length);
            assertThrows(EOFException.class, () -> AudioWire.readFrame(new ByteArrayInputStream(truncated), 0));
        }
    }
    @Test public void rejectsHeaderMagicVersionLengthRateChannelsAndEncoding() throws IOException {
        byte[] bytes = header(); bytes[0] ^= 1; badHeader(bytes);
        bytes = header(); bytes[5] = 2; badHeader(bytes);
        bytes = header(); bytes[7] = 31; badHeader(bytes);
        bytes = header(); ByteBuffer.wrap(bytes).putInt(8, 48000); badHeader(bytes);
        bytes = header(); bytes[13] = 2; badHeader(bytes);
        bytes = header(); bytes[15] = 8; badHeader(bytes);
    }
    @Test public void rejectsOutOfOrderRepeatedMissingAndNegativePeerSequences() throws IOException {
        badFrame(frame(0), 1); badFrame(frame(2), 1); badFrame(frame(1), 0);
        byte[] negative = frame(0); ByteBuffer.wrap(negative).putLong(8, -1); badFrame(negative, 0);
        byte[] badPosition = frame(1); ByteBuffer.wrap(badPosition).putLong(16, 3999); badFrame(badPosition, 1);
    }
    @Test public void rejectsArbitraryPeerLengthsBeforeReadingOrAllocatingPayload() throws IOException {
        for (int length : new int[]{Integer.MIN_VALUE, -1, 0, 8000, AudioWire.FRAME_BODY_BYTES + 1, Integer.MAX_VALUE}) {
            ByteArrayOutputStream prefix = new ByteArrayOutputStream();
            DataOutputStream data = new DataOutputStream(prefix);
            data.writeInt(0x54574146); data.writeInt(length);
            badFrame(prefix.toByteArray(), 0);
        }
        byte[] magic = frame(0); magic[0] ^= 1; badFrame(magic, 0);
    }
    @Test public void boundedFragmentedAndZeroReturningReadsStillWork() throws IOException {
        byte[] bytes = frame(7);
        InputStream fragmented = new ByteArrayInputStream(bytes) {
            @Override public synchronized int read(byte[] b, int off, int length) {
                return super.read(b, off, Math.min(3, length));
            }
        };
        assertArrayEquals(samples(), AudioWire.readFrame(fragmented, 7));
        InputStream zeros = new ByteArrayInputStream(bytes) {
            private boolean zero;
            @Override public synchronized int read(byte[] b, int off, int length) {
                zero = !zero; return zero ? 0 : super.read(b, off, Math.min(7, length));
            }
        };
        assertArrayEquals(samples(), AudioWire.readFrame(zeros, 7));
    }
    @Test public void rejectsInvalidWriterArgumentsAndOverflowWithoutOutput() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertThrows(IllegalArgumentException.class, () -> AudioWire.writeFrame(out, -1, samples()));
        assertThrows(IllegalArgumentException.class, () -> AudioWire.writeFrame(out, Long.MAX_VALUE, samples()));
        assertThrows(IllegalArgumentException.class, () -> AudioWire.writeFrame(out, 0, new short[3999]));
        assertThrows(IllegalArgumentException.class, () -> AudioWire.writeHeader(out, "1-1-1-1-1"));
        assertEquals(0, out.size());
    }
    @Test public void maximumRepresentableSequenceRoundTrips() throws IOException {
        long sequence = Long.MAX_VALUE / AudioWire.FRAME_SAMPLES;
        assertArrayEquals(samples(), AudioWire.readFrame(new ByteArrayInputStream(frame(sequence)), sequence));
    }
    @Test public void methodsNeverCloseOrFlushOwnedTransport() throws IOException {
        class Owned extends ByteArrayOutputStream {
            boolean closed, flushed;
            @Override public void close() { closed = true; }
            @Override public void flush() { flushed = true; }
        }
        Owned out = new Owned(); AudioWire.writeHeader(out, UUID.randomUUID().toString());
        AudioWire.writeFrame(out, 0, samples()); assertFalse(out.closed); assertFalse(out.flushed);
    }
}
