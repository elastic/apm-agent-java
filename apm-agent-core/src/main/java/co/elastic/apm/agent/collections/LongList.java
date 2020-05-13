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
package co.elastic.apm.agent.collections;

import java.util.Arrays;

public class LongList {
    private static final int DEFAULT_CAPACITY = 16;
    private long[] longs;
    private int size;

    public LongList() {
        this(DEFAULT_CAPACITY);
    }

    public LongList(int initialCapacity) {
        longs = new long[initialCapacity];
    }

    public static LongList of(long... values) {
        LongList list = new LongList(values.length);
        for (long value : values) {
            list.add(value);
        }
        return list;
    }

    public void add(long l) {
        ensureCapacity(size + 1);
        longs[size++] = l;
    }

    public void addAll(LongList other) {
        ensureCapacity(size + other.size);
        System.arraycopy(other.longs, 0, longs, size, other.size);
        size += other.size;
    }

    private void ensureCapacity(int size) {
        if (longs.length < size) {
            longs = Arrays.copyOf(longs, longs.length * 2);
        }
    }

    public int getSize() {
        return size;
    }

    public long get(int i) {
        if (i >= size) {
            throw new IndexOutOfBoundsException();
        }
        return longs[i];
    }

    public boolean contains(long l) {
        for (int i = 0; i < size; i++) {
            if (longs[i] == l) {
                return true;
            }
        }
        return false;
    }

    public boolean remove(long l) {
        for (int i = size - 1; i >= 0; i--) {
            if (longs[i] == l) {
                remove(i);
                return true;
            }
        }
        return false;
    }

    public long remove(int i) {
        long previousValue = get(i);
        size--;
        if (size > i) {
            System.arraycopy(longs, i + 1 , longs, i, size - i);
        }
        longs[size] = 0;
        return previousValue;
    }

    public void clear() {
        Arrays.fill(longs, 0);
        size = 0;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < size; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(longs[i]);
        }
        sb.append(']');
        return sb.toString();
    }

    public long[] toArray() {
        return Arrays.copyOfRange(longs, 0, size);
    }
}
