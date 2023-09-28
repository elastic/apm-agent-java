/*
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package co.elastic.apm.agent.tracer.direct;

import co.elastic.apm.agent.tracer.Id;
import co.elastic.apm.agent.tracer.util.HexUtils;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class TestId implements Id {

    private final byte[] data;
    private boolean empty = true;

    public static TestId new128BitId() {
        return new TestId(16);
    }

    public static TestId new64BitId() {
        return new TestId(8);
    }

    private TestId(int idLengthBytes) {
        data = new byte[idLengthBytes];
    }

    @Override
    public void setToRandomValue() {
        setToRandomValue(ThreadLocalRandom.current());
    }

    public void setToRandomValue(Random random) {
        random.nextBytes(data);
        onMutation(false);
    }

    public void fromLongs(long... values) {
        if (values.length * Long.BYTES != data.length) {
            throw new IllegalArgumentException("Invalid number of long values");
        }
        final ByteBuffer buffer = ByteBuffer.wrap(data);
        for (long value : values) {
            buffer.putLong(value);
        }
        onMutation();
    }

    public void copyFrom(TestId other) {
        System.arraycopy(other.data, 0, data, 0, data.length);
        this.empty = other.empty;
    }

    private void onMutation() {
        onMutation(isAllZeros(data));
    }

    private void onMutation(boolean empty) {
        this.empty = empty;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TestId that = (TestId) o;
        return Arrays.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(data);
    }

    @Override
    public String toString() {
        return HexUtils.bytesToHex(data);
    }

    @Override
    public boolean isEmpty() {
        return empty;
    }

    private static boolean isAllZeros(byte[] bytes) {
        for (byte b : bytes) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }

    @Override
    public long getLeastSignificantBits() {
        return readLong(data.length - 8);
    }

    private long readLong(int offset) {
        long lsb = 0;
        for (int i = offset; i < offset + 8; i++) {
            lsb = (lsb << 8) | (data[i] & 0xff);
        }
        return lsb;
    }
}
