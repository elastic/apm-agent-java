package co.elastic.apm.impl.transaction;

import co.elastic.apm.objectpool.Recyclable;
import co.elastic.apm.util.HexUtils;
import com.fasterxml.jackson.annotation.JsonValue;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A 64 bit random id which is used as a unique id for {@link Span}s within a {@link Transaction}
 */
public class SpanId implements Recyclable {

    private static final int LENGTH = 8;
    private final byte[] data = new byte[LENGTH];

    public void setToRandomValue() {
        setToRandomValue(ThreadLocalRandom.current());
    }

    public void setToRandomValue(Random random) {
        random.nextBytes(data);
    }

    public void setLong(long l) {
        for (int i = 7; i >= 0; i--) {
            data[i] = (byte) (l & 0xFF);
            l >>= 8;
        }
    }

    @Override
    public void resetState() {
        for (int i = 0; i < data.length; i++) {
            data[i] = 0;
        }
    }

    /**
     * Returns the span id as a <code>long</code>
     *
     * @return the span id as a <code>long</code>
     */
    @JsonValue
    public long asLong() {
        long l = 0;
        for (int i = 0; i < 8; i++) {
            l = (l << 8) | (data[i] & 0xff);
        }
        return l;
    }

    public String toHexEncodedString() {
        return HexUtils.bytesToHex(data);
    }

    public void writeToOutputStream(OutputStream outputStream) throws IOException {
        HexUtils.writeBytesAsHex(data, outputStream);
    }

    public void copyFrom(SpanId other) {
        System.arraycopy(other.data, 0, data, 0, LENGTH);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SpanId spanId = (SpanId) o;
        return Arrays.equals(data, spanId.data);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(data);
    }
}
