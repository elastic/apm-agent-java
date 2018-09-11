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
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A 64 bit random id which is used as a unique id for {@link Span}s within a {@link Transaction}
 */
public class SpanId implements Recyclable {

    private static final int LENGTH = 8;
    private final byte[] data = new byte[LENGTH];
    @Nullable
    private String cachedStringRepresentation;

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
        cachedStringRepresentation = null;
    }

    /**
     * Returns the span id as a {@code long}
     *
     * @return the span id as a {@code long}
     */
    public long asLong() {
        long l = 0;
        for (int i = 0; i < 8; i++) {
            l = (l << 8) | (data[i] & 0xff);
        }
        return l;
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

    public byte[] getBytes() {
        return data;
    }

    @Override
    public String toString() {
        String s = cachedStringRepresentation;
        if (s == null) {
            s = cachedStringRepresentation = HexUtils.bytesToHex(data);
        }
        return s;
    }
}
