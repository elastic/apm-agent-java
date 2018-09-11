/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.impl.transaction;

import co.elastic.apm.objectpool.Recyclable;
import co.elastic.apm.util.HexUtils;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A 128 bit globally unique ID of the whole trace forest
 */
public class TraceId implements Recyclable {

    private final static TraceId EMPTY = new TraceId();

    private static final int SIZE = 16;
    private final byte[] data = new byte[SIZE];
    @Nullable
    private String cachedStringRepresentation;

    public void setToRandomValue() {
        setToRandomValue(ThreadLocalRandom.current());
    }

    public void setToRandomValue(Random random) {
        random.nextBytes(data);
    }

    public void setValue(long mostSignificantBits, long leastSignificantBits) {
        ByteBuffer.wrap(data).putLong(mostSignificantBits).putLong(leastSignificantBits);
    }

    @Override
    public void resetState() {
        for (int i = 0; i < data.length; i++) {
            data[i] = 0;
        }
        cachedStringRepresentation = null;
    }

    public void copyFrom(TraceId other) {
        System.arraycopy(other.data, 0, data, 0, SIZE);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TraceId that = (TraceId) o;
        return Arrays.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(data);
    }

    @Override
    public String toString() {
        String s = cachedStringRepresentation;
        if (s == null) {
            s = cachedStringRepresentation = HexUtils.bytesToHex(data);
        }
        return s;
    }

    public boolean isEmpty() {
        return EMPTY.equals(this);
    }

    /**
     * Returns the mutable underlying byte array
     *
     * @return the mutable underlying byte array
     */
    public byte[] getBytes() {
        return data;
    }

    /**
     * Returns the first 8 bytes of this transaction id as a {@code long}
     *
     * @return the first 8 bytes of this transaction id as a {@code long}
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
}
