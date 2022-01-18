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
package co.elastic.apm.agent.util;

import co.elastic.apm.agent.objectpool.Recyclable;
import co.elastic.apm.agent.report.serialize.DslJsonSerializer;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import javax.annotation.Nullable;
import java.nio.Buffer;
import java.nio.CharBuffer;
import java.nio.charset.CoderResult;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * A map (not implementing the java.util.Map interface) that only supports String-byte[] pair additions and iterations.
 * The map doesn't allocate during addition or iteration.
 * This map does not support any form of concurrency. It can be either be in a write mode (through its {@link #add}
 * method) or read mode (through the {@link #iterator()} API) at a given time. Reads and writes must not be
 * performed by more than one thread concurrently.
 * <p>
 * NOTE: this map does not guarantee visibility, therefore ensuring visibility when switching from read to write mode
 * (or the other way around) is under the responsibility of the map's user.
 */
public class BinaryHeaderMap implements Recyclable, Iterable<BinaryHeaderMap.Entry> {
    public static final int MAXIMUM_HEADER_BUFFER_SIZE = DslJsonSerializer.MAX_VALUE_LENGTH * 10;

    private static final Logger logger = LoggerFactory.getLogger(BinaryHeaderMap.class);

    static final int INITIAL_CAPACITY = 10;

    private CharBuffer valueBuffer;
    /**
     * Ordered list of keys
     */
    private final ArrayList<String> keys;

    /**
     * Values lengths, key index is provided with {@link #keys}.
     * Negative length indicate a {@literal null} entry
     */
    private int[] valueLengths;

    private final NoGarbageIterator iterator;

    public BinaryHeaderMap() {
        valueBuffer = CharBuffer.allocate(64);
        keys = new ArrayList<>(INITIAL_CAPACITY);
        valueLengths = new int[INITIAL_CAPACITY];
        iterator = new NoGarbageIterator();
    }

    public int size() {
        return keys.size();
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public boolean add(String key, @Nullable byte[] value) {
        boolean result;
        if (value == null) {
            addNewEntry(key, -1);
            return true;
        }

        int valuesPos = valueBuffer.position();
        CoderResult coderResult = IOUtils.decodeUtf8Bytes(value, valueBuffer);
        while (coderResult.isOverflow()) {
            ((Buffer) valueBuffer).limit(valuesPos);
            if (!enlargeBuffer()) {
                return false;
            }
            coderResult = IOUtils.decodeUtf8Bytes(value, valueBuffer);
        }

        if (coderResult.isError()) {
            ((Buffer) valueBuffer).limit(valuesPos);
            result = false;
        } else {
            addNewEntry(key, valueBuffer.position() - valuesPos);
            result = true;
        }

        return result;
    }

    /**
     * Adds a new entry and increase storage capacity if needed
     *
     * @param key         key
     * @param valueLength value length
     */
    private void addNewEntry(String key, int valueLength) {
        int size = keys.size();
        keys.add(key);
        if (size == valueLengths.length) {
            enlargeValueLengths();
        }
        valueLengths[size] = valueLength;
    }

    private boolean enlargeBuffer() {
        if (valueBuffer.capacity() == MAXIMUM_HEADER_BUFFER_SIZE) {
            logger.debug("Headers buffer reached its maximal size ({}) and cannot be further enlarged", MAXIMUM_HEADER_BUFFER_SIZE);
            return false;
        }
        int newCapacity = Math.min(valueBuffer.capacity() * 2, MAXIMUM_HEADER_BUFFER_SIZE);
        CharBuffer newBuffer = CharBuffer.allocate(newCapacity);
        ((Buffer) valueBuffer).flip();
        newBuffer.put(valueBuffer);
        valueBuffer = newBuffer;
        return true;
    }

    private void enlargeValueLengths() {
        int[] newValueOffsets = new int[valueLengths.length * 2];
        System.arraycopy(valueLengths, 0, newValueOffsets, 0, valueLengths.length);
        valueLengths = newValueOffsets;
    }

    @Override
    public void resetState() {
        keys.clear();
        ((Buffer) valueBuffer).clear();
        iterator.reset();
    }

    @Override
    public Iterator<Entry> iterator() {
        iterator.reset();
        ((Buffer) valueBuffer).flip();
        return iterator;
    }

    public void copyFrom(BinaryHeaderMap other) {
        resetState();
        keys.addAll(other.keys);
        ((Buffer) other.valueBuffer).flip();
        if (valueBuffer.capacity() < other.valueBuffer.remaining()) {
            valueBuffer = CharBuffer.allocate(other.valueBuffer.remaining());
        }
        valueBuffer.put(other.valueBuffer);
        if (this.valueLengths.length < other.valueLengths.length) {
            this.valueLengths = new int[other.valueLengths.length];
        }
        System.arraycopy(other.valueLengths, 0, this.valueLengths, 0, other.valueLengths.length);
    }

    public static class Entry {
        @Nullable
        String key;
        CharBuffer value = CharBuffer.allocate(64);
        private boolean nullValue;

        public String getKey() {
            if (key == null) {
                throw new IllegalStateException("Key shouldn't be null. Make sure you don't read and write to this map concurrently");
            }
            return key;
        }

        @Nullable
        public CharSequence getValue() {
            return nullValue ? null : value;
        }

        void setValue(@Nullable CharBuffer valueBuffer) {
            if (valueBuffer == null) {
                nullValue = true;
            } else {
                nullValue = false;
                ((Buffer) value).clear();
                int remaining = valueBuffer.remaining();
                if (remaining > value.capacity()) {
                    if (value.capacity() < DslJsonSerializer.MAX_VALUE_LENGTH) {
                        enlargeBuffer();
                    }
                    if (remaining > DslJsonSerializer.MAX_VALUE_LENGTH) {
                        ((Buffer) valueBuffer).limit(valueBuffer.position() + DslJsonSerializer.MAX_VALUE_LENGTH);
                    }
                }
                value.put(valueBuffer);
                ((Buffer) value).flip();
            }

        }

        void reset() {
            key = null;
            ((Buffer) value).clear();
        }

        private void enlargeBuffer() {
            CharBuffer newBuffer = CharBuffer.allocate(DslJsonSerializer.MAX_VALUE_LENGTH);
            ((Buffer) value).flip();
            newBuffer.put(value);
            value = newBuffer;
        }
    }

    private class NoGarbageIterator implements Iterator<Entry> {
        int index = 0;
        int nextValueOffset = 0;
        final Entry entry = new Entry();

        @Override
        public boolean hasNext() {
            return index < keys.size();
        }

        @Override
        public Entry next() {
            entry.reset();
            entry.key = keys.get(index);
            int valueLength = valueLengths[index];

            if(valueLength < 0){
                entry.setValue(null);
            } else {
                int thisValueOffset = nextValueOffset;
                nextValueOffset += valueLengths[index];
                ((Buffer) valueBuffer).limit(nextValueOffset);
                ((Buffer) valueBuffer).position(thisValueOffset);
                entry.setValue(valueBuffer);
            }

            index++;
            return entry;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        void reset() {
            index = 0;
            nextValueOffset = 0;
            entry.reset();
        }
    }
}
