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

import java.util.Arrays;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A 128 bit random id which is used as a globally unique id for {@link Transaction}s
 */
@Deprecated
public class TransactionId implements Recyclable {

    private final static TransactionId EMPTY = new TransactionId();

    public static final int SIZE = 16;
    private final byte[] data = new byte[SIZE];

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

    public String toHexEncodedString() {
        return HexUtils.bytesToHex(data);
    }

    public void copyFrom(TransactionId other) {
        System.arraycopy(other.data, 0, data, 0, SIZE);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TransactionId that = (TransactionId) o;
        return Arrays.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(data);
    }

    @Override
    public String toString() {
        return toHexEncodedString();
    }

    public UUID toUUID() {
        return new UUID(getMostSignificantBits(), getLeastSignificantBits());
    }

    public boolean isEmpty() {
        return EMPTY.equals(this);
    }

    public byte[] getBytes() {
        return data;
    }
}
