/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
 * %%
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
 * #L%
 */
package co.elastic.apm.agent.util;

import co.elastic.apm.agent.objectpool.Recyclable;
import co.elastic.apm.agent.report.serialize.DslJsonSerializer;

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

    private CharBuffer valueBuffer;
    private final ArrayList<String> keys;
    private int[] valueLengths;
    private final NoGarbageIterator iterator;

    public BinaryHeaderMap() {
        valueBuffer = CharBuffer.allocate(64);
        keys = new ArrayList<>(10);
        valueLengths = new int[10];
        iterator = new NoGarbageIterator();
    }

    public int size() {
        return keys.size();
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public boolean add(String key, byte[] value) throws InsufficientCapacityException {
        int valuesPos = valueBuffer.position();
        CoderResult coderResult = IOUtils.decodeUtf8Bytes(value, valueBuffer);
        while (coderResult.isOverflow()) {
            ((Buffer) valueBuffer).limit(valuesPos);
            enlargeBuffer();
            coderResult = IOUtils.decodeUtf8Bytes(value, valueBuffer);
        }
        boolean result;
        if (coderResult.isError()) {
            ((Buffer) valueBuffer).limit(valuesPos);
            result = false;
        } else {
            int size = keys.size();
            keys.add(key);
            if (size == valueLengths.length) {
                enlargeValueLengths();
            }
            valueLengths[size] = valueBuffer.position() - valuesPos;
            result = true;
        }
        return result;
    }

    private void enlargeBuffer() throws InsufficientCapacityException {
        if (valueBuffer.capacity() == MAXIMUM_HEADER_BUFFER_SIZE) {
            throw new InsufficientCapacityException();
        }
        int newCapacity = Math.min(valueBuffer.capacity() * 2, MAXIMUM_HEADER_BUFFER_SIZE);
        CharBuffer newBuffer = CharBuffer.allocate(newCapacity);
        ((Buffer) valueBuffer).flip();
        newBuffer.put(valueBuffer);
        valueBuffer = newBuffer;
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

        public String getKey() {
            if (key == null) {
                throw new IllegalStateException("Key shouldn't be null. Make sure you don't read and write to this map concurrently");
            }
            return key;
        }

        public CharSequence getValue() {
            return value;
        }

        void setValue(CharBuffer valueBuffer) {
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
            int thisValueOffset = nextValueOffset;
            nextValueOffset += valueLengths[index];
            ((Buffer) valueBuffer).limit(nextValueOffset);
            ((Buffer) valueBuffer).position(thisValueOffset);
            entry.setValue(valueBuffer);
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

    public static class InsufficientCapacityException extends Exception {
        public InsufficientCapacityException() {
            super("Headers buffer is to large, cannot append anymore headers");
        }
    }
}
