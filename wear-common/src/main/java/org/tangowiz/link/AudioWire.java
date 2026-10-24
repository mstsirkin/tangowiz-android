package org.tangowiz.link;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/**
 * Fixed-size, versioned mono PCM transport. Framing integers use network byte
 * order; signed PCM16 samples use little endian. Methods never flush or close
 * the supplied stream. Callers must serialize writes and close the channel to
 * cancel a blocked read. They must end the stream on any IOException: there is
 * no byte scanning, resynchronization, or retry of malformed frames.
 */
public final class AudioWire {
    public static final int SAMPLE_RATE = 8000;
    public static final int FRAME_SAMPLES = 4000;
    public static final int CHANNELS = 1;
    public static final int PCM_BITS = 16;
    public static final int PROTOCOL_VERSION = 1;
    public static final int HEADER_BYTES = 32;
    public static final int FRAME_PAYLOAD_BYTES = FRAME_SAMPLES * 2;
    public static final int FRAME_BODY_BYTES = 16 + FRAME_PAYLOAD_BYTES;
    public static final int FRAME_BYTES = 8 + FRAME_BODY_BYTES;
    private static final int HEADER_MAGIC = 0x54574150; // TWAP
    private static final int FRAME_MAGIC = 0x54574146;  // TWAF
    private static final long MAX_SEQUENCE = Long.MAX_VALUE / FRAME_SAMPLES;
    private AudioWire() {}

    /** Header: TWAP,u16 version,u16 bytes,u32 rate,u16 channels,u16 bits,UUID128. */
    public static void writeHeader(OutputStream output, String streamId) throws IOException {
        if (streamId == null) throw new IllegalArgumentException("Stream UUID is required");
        UUID uuid = UUID.fromString(streamId);
        if (!uuid.toString().equalsIgnoreCase(streamId))
            throw new IllegalArgumentException("Stream UUID must use canonical notation");
        DataOutputStream out = new DataOutputStream(output);
        out.writeInt(HEADER_MAGIC);
        out.writeShort(PROTOCOL_VERSION);
        out.writeShort(HEADER_BYTES);
        out.writeInt(SAMPLE_RATE);
        out.writeShort(CHANNELS);
        out.writeShort(PCM_BITS);
        out.writeLong(uuid.getMostSignificantBits());
        out.writeLong(uuid.getLeastSignificantBits());
    }

    /** Returns the stream UUID; truncated/closed streams raise EOFException. */
    public static String readHeader(InputStream input) throws IOException {
        DataInputStream in = new DataInputStream(input);
        if (in.readInt() != HEADER_MAGIC) throw new IOException("Invalid audio header magic");
        if (in.readUnsignedShort() != PROTOCOL_VERSION) throw new IOException("Unsupported audio protocol");
        if (in.readUnsignedShort() != HEADER_BYTES) throw new IOException("Invalid audio header length");
        if (in.readInt() != SAMPLE_RATE) throw new IOException("Audio sample rate must be 8000 Hz");
        if (in.readUnsignedShort() != CHANNELS) throw new IOException("Audio must be mono");
        if (in.readUnsignedShort() != PCM_BITS) throw new IOException("Audio must use signed PCM16");
        return new UUID(in.readLong(), in.readLong()).toString();
    }

    /** Frame: TWAF,u32 fixed body bytes,u64 sequence,u64 sample position,PCM8000. */
    public static void writeFrame(OutputStream output, long sequence, short[] samples) throws IOException {
        checkSequence(sequence);
        if (samples == null || samples.length != FRAME_SAMPLES)
            throw new IllegalArgumentException("Audio frame must contain exactly 4000 samples");
        DataOutputStream out = new DataOutputStream(output);
        out.writeInt(FRAME_MAGIC);
        out.writeInt(FRAME_BODY_BYTES);
        out.writeLong(sequence);
        out.writeLong(sequence * FRAME_SAMPLES);
        byte[] pcm = new byte[FRAME_PAYLOAD_BYTES];
        for (int i = 0; i < FRAME_SAMPLES; i++) {
            pcm[2 * i] = (byte) samples[i];
            pcm[2 * i + 1] = (byte) (samples[i] >>> 8);
        }
        out.write(pcm);
    }

    /**
     * Reads exactly one frame; the caller increments expectedSequence only on
     * success. Repeats, gaps, reordered frames and inconsistent sample positions
     * are errors. EOF, including an otherwise clean disconnect, is explicit.
     */
    public static short[] readFrame(InputStream input, long expectedSequence) throws IOException {
        checkSequence(expectedSequence);
        DataInputStream in = new DataInputStream(input);
        if (in.readInt() != FRAME_MAGIC) throw new IOException("Invalid audio frame magic");
        if (in.readInt() != FRAME_BODY_BYTES) throw new IOException("Invalid audio frame length");
        long sequence = in.readLong();
        if (sequence != expectedSequence) throw new IOException("Audio frame is missing, repeated, or out of order");
        if (in.readLong() != sequence * FRAME_SAMPLES) throw new IOException("Invalid audio sample position");
        // The peer cannot choose any allocation size.
        byte[] pcm = new byte[FRAME_PAYLOAD_BYTES];
        readFully(input, pcm);
        short[] samples = new short[FRAME_SAMPLES];
        for (int i = 0; i < FRAME_SAMPLES; i++)
            samples[i] = (short) ((pcm[2 * i] & 255) | ((pcm[2 * i + 1] & 255) << 8));
        return samples;
    }

    private static void checkSequence(long sequence) {
        if (sequence < 0 || sequence > MAX_SEQUENCE)
            throw new IllegalArgumentException("Audio frame sequence is out of range");
    }

    private static void readFully(InputStream input, byte[] bytes) throws IOException {
        int position = 0;
        while (position < bytes.length) {
            int n = input.read(bytes, position, bytes.length - position);
            if (n < 0) throw new EOFException("Audio frame payload is truncated");
            if (n == 0) {
                // Avoid spinning on a nonconforming InputStream returning zero.
                int value = input.read();
                if (value < 0) throw new EOFException("Audio frame payload is truncated");
                bytes[position++] = (byte) value;
            } else {
                position += n;
            }
        }
    }
}
