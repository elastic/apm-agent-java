package co.elastic.apm.impl.transaction;

import co.elastic.apm.objectpool.Recyclable;
import co.elastic.apm.util.HexUtils;
import com.fasterxml.jackson.annotation.JsonValue;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A 128 bit random id which is used as a globally unique id for {@link Transaction}s
 */
public class TransactionId implements Recyclable {

    private final byte[] data = new byte[16];

    public void setToRandomValue() {
        setToRandomValue(ThreadLocalRandom.current());
    }

    public void setToRandomValue(Random random) {
        random.nextBytes(data);
    }

    @Override
    public void resetState() {
        for (int i = 0; i < data.length; i++) {
            data[i] = 0;
        }
    }

    /**
     * Returns the first 8 bytes of this transaction id as a <code>long</code>
     *
     * @return the first 8 bytes of this transaction id as a <code>long</code>
     */
    public long getMostSignificantBits() {
        long msb = 0;
        for (int i = 0; i < 8; i++) {
            msb = (msb << 8) | (data[i] & 0xff);
        }
        return msb;
    }

    public long getLeastSignificantBits() {
        long lsb = 0;
        for (int i = 8; i < 16; i++) {
            lsb = (lsb << 8) | (data[i] & 0xff);
        }
        return lsb;
    }

    @JsonValue
    public UUID toUuid() {
        return new UUID(getMostSignificantBits(), getLeastSignificantBits());
    }

    public String toHexEncodedString() {
        return HexUtils.bytesToHex(data);
    }

    public void writeToOutputStream(OutputStream outputStream) throws IOException {
        HexUtils.writeBytesAsHex(data, outputStream);
    }
}
