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
package co.elastic.apm.agent.impl.transaction;

import co.elastic.apm.agent.util.HexUtils;
import co.elastic.apm.agent.tracer.pooling.Recyclable;
import com.dslplatform.json.JsonWriter;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A 128 bit globally unique ID of the whole trace forest
 */
public class Id implements Recyclable, co.elastic.apm.agent.tracer.Id {

    private final byte[] data;
    private boolean empty = true;
    @Nullable
    private String cachedStringRepresentation;

    public static Id new128BitId() {
        return new Id(16);
    }

    public static Id new64BitId() {
        return new Id(8);
    }

    private Id(int idLengthBytes) {
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

    public void fromHexString(String hexEncodedString, int offset) {
        HexUtils.nextBytes(hexEncodedString, offset, data);
        onMutation();
    }

    /**
     * Sets the id based on a byte array
     *
     * @param bytes the byte array used to fill this id's {@link #data}
     * @param offset the offset in the byte array
     * @return the number of read bytes which is equivalent to {@link #getLength()}
     */
    public int fromBytes(byte[] bytes, int offset) {
        System.arraycopy(bytes, offset, data, 0, data.length);
        onMutation();
        return data.length;
    }

    public int toBytes(byte[] bytes, int offset) {
        System.arraycopy(data, 0, bytes, offset, data.length);
        return offset + data.length;
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

    @Override
    public void resetState() {
        Arrays.fill(data, (byte) 0);
        onMutation(true);
    }

    public void copyFrom(Id other) {
        System.arraycopy(other.data, 0, data, 0, data.length);
        this.cachedStringRepresentation = other.cachedStringRepresentation;
        this.empty = other.empty;
    }

    private void onMutation() {
        onMutation(isAllZeros(data));
    }

    private void onMutation(boolean empty) {
        cachedStringRepresentation = null;
        this.empty = empty;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Id that = (Id) o;
        return Arrays.equals(data, that.data);
    }

    public boolean dataEquals(byte[] data, int offset) {
        byte[] thisData = this.data;
        for (int i = 0; i < thisData.length; i++) {
            if (thisData[i] != data[i + offset]) {
                return false;
            }
        }
        return true;
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

    public void writeAsHex(JsonWriter jw) {
        HexUtils.writeBytesAsHex(data, jw);
    }

    public void writeAsHex(StringBuilder sb) {
        HexUtils.writeBytesAsHex(data, sb);
    }

    /**
     * Returns the last 8 bytes of this id as a {@code long}.
     * <p>
     * The least significant bits (the right part) of an id is preferred to be used for making random sampling decisions.
     * </p>
     * <p>
     * "There are systems that make random sampling decisions based on the value of trace-id.
     * So to increase interoperability it is recommended to keep the random part on the right side of trace-id value."
     * </p>
     * @see <a href="https://github.com/w3c/distributed-tracing/blob/master/trace_context/HTTP_HEADER_FORMAT.md#trace-id">W3C trace context spec</a>
     * @return the last 8 bytes of this id as a {@code long}
     */
    public long getLeastSignificantBits() {
        return readLong(data.length - 8);
    }

    /**
     * Converts the next 8 bytes, starting from the offset, to a {@code long}
     */
    public long readLong(int offset) {
        long lsb = 0;
        for (int i = offset; i < offset + 8; i++) {
            lsb = (lsb << 8) | (data[i] & 0xff);
        }
        return lsb;
    }

    int getLength() {
        return data.length;
    }
}
