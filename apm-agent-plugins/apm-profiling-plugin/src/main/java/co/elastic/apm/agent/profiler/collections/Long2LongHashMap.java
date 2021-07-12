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
/*
 * Copyright 2014-2020 Real Logic Limited.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package co.elastic.apm.agent.profiler.collections;

import java.io.Serializable;
import java.util.AbstractCollection;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

import static co.elastic.apm.agent.profiler.collections.CollectionUtil.findNextPositivePowerOfTwo;
import static co.elastic.apm.agent.profiler.collections.CollectionUtil.validateLoadFactor;

/**
 * A open addressing with linear probing hash map specialised for primitive key and value pairs.
 */
public class Long2LongHashMap implements Map<Long, Long>, Serializable
{
    static final int MIN_CAPACITY = 8;

    private final float loadFactor;
    private final long missingValue;
    private int resizeThreshold;
    private int size = 0;
    private final boolean shouldAvoidAllocation;

    private long[] entries;
    private KeySet keySet;
    private ValueCollection values;
    private EntrySet entrySet;

    public Long2LongHashMap(final long missingValue)
    {
        this(MIN_CAPACITY, Hashing.DEFAULT_LOAD_FACTOR, missingValue);
    }

    public Long2LongHashMap(
        final int initialCapacity,
        final float loadFactor,
        final long missingValue)
    {
        this(initialCapacity, loadFactor, missingValue, true);
    }

    /**
     * @param initialCapacity       for the map to override {@link #MIN_CAPACITY}
     * @param loadFactor            for the map to override {@link Hashing#DEFAULT_LOAD_FACTOR}.
     * @param missingValue          for the map that represents null.
     * @param shouldAvoidAllocation should allocation be avoided by caching iterators and map entries.
     */
    public Long2LongHashMap(
        final int initialCapacity,
        final float loadFactor,
        final long missingValue,
        final boolean shouldAvoidAllocation)
    {
        validateLoadFactor(loadFactor);

        this.loadFactor = loadFactor;
        this.missingValue = missingValue;
        this.shouldAvoidAllocation = shouldAvoidAllocation;

        capacity(findNextPositivePowerOfTwo(Math.max(MIN_CAPACITY, initialCapacity)));
    }

    /**
     * The value to be used as a null marker in the map.
     *
     * @return value to be used as a null marker in the map.
     */
    public long missingValue()
    {
        return missingValue;
    }

    /**
     * Get the load factor applied for resize operations.
     *
     * @return the load factor applied for resize operations.
     */
    public float loadFactor()
    {
        return loadFactor;
    }

    /**
     * Get the total capacity for the map to which the load factor will be a fraction of.
     *
     * @return the total capacity for the map.
     */
    public int capacity()
    {
        return entries.length >> 2;
    }

    /**
     * Get the actual threshold which when reached the map will resize.
     * This is a function of the current capacity and load factor.
     *
     * @return the threshold when the map will resize.
     */
    public int resizeThreshold()
    {
        return resizeThreshold;
    }

    /**
     * {@inheritDoc}
     */
    public int size()
    {
        return size;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isEmpty()
    {
        return size == 0;
    }

    public long get(final long key)
    {
        final long[] entries = this.entries;
        final long missingValue = this.missingValue;
        final int mask = entries.length - 1;
        int index = Hashing.evenHash(key, mask);

        long value = missingValue;
        while (entries[index + 1] != missingValue)
        {
            if (entries[index] == key)
            {
                value = entries[index + 1];
                break;
            }

            index = next(index, mask);
        }

        return value;
    }

    /**
     * Put a key value pair in the map.
     *
     * @param key   lookup key
     * @param value new value, must not be initialValue
     * @return current counter value associated with key, or initialValue if none found
     * @throws IllegalArgumentException if value is missingValue
     */
    public long put(final long key, final long value)
    {
        if (value == missingValue)
        {
            throw new IllegalArgumentException("cannot accept missingValue");
        }

        final long[] entries = this.entries;
        final long missingValue = this.missingValue;
        final int mask = entries.length - 1;
        int index = Hashing.evenHash(key, mask);
        long oldValue = missingValue;

        while (entries[index + 1] != missingValue)
        {
            if (entries[index] == key)
            {
                oldValue = entries[index + 1];
                break;
            }

            index = next(index, mask);
        }

        if (oldValue == missingValue)
        {
            ++size;
            entries[index] = key;
        }

        entries[index + 1] = value;

        increaseCapacity();

        return oldValue;
    }

    private void increaseCapacity()
    {
        if (size > resizeThreshold)
        {
            // entries.length = 2 * capacity
            final int newCapacity = entries.length;
            rehash(newCapacity);
        }
    }

    private void rehash(final int newCapacity)
    {
        final long[] oldEntries = entries;
        final long missingValue = this.missingValue;
        final int length = entries.length;

        capacity(newCapacity);

        final long[] newEntries = entries;
        final int mask = entries.length - 1;

        for (int keyIndex = 0; keyIndex < length; keyIndex += 2)
        {
            final long value = oldEntries[keyIndex + 1];
            if (value != missingValue)
            {
                final long key = oldEntries[keyIndex];
                int index = Hashing.evenHash(key, mask);

                while (newEntries[index + 1] != missingValue)
                {
                    index = next(index, mask);
                }

                newEntries[index] = key;
                newEntries[index + 1] = value;
            }
        }
    }

    /**
     * Primitive specialised forEach implementation.
     * <p>
     * NB: Renamed from forEach to avoid overloading on parameter types of lambda
     * expression, which doesn't play well with type inference in lambda expressions.
     *
     * @param consumer a callback called for each key/value pair in the map.
     */
    public void longForEach(final LongLongConsumer consumer)
    {
        final long[] entries = this.entries;
        final long missingValue = this.missingValue;
        final int length = entries.length;

        for (int keyIndex = 0; keyIndex < length; keyIndex += 2)
        {
            if (entries[keyIndex + 1] != missingValue) // lgtm [java/index-out-of-bounds]
            {
                consumer.accept(entries[keyIndex], entries[keyIndex + 1]); // lgtm [java/index-out-of-bounds]
            }
        }
    }

    /**
     * Long primitive specialised containsKey.
     *
     * @param key the key to check.
     * @return true if the map contains key as a key, false otherwise.
     */
    public boolean containsKey(final long key)
    {
        return get(key) != missingValue;
    }

    /**
     * Does the map contain the value.
     *
     * @param value to be tested against contained values.
     * @return true if contained otherwise value.
     */
    public boolean containsValue(final long value)
    {
        boolean found = false;
        if (value != missingValue)
        {
            final long[] entries = this.entries;
            final int length = entries.length;

            for (int valueIndex = 1; valueIndex < length; valueIndex += 2)
            {
                if (value == entries[valueIndex])
                {
                    found = true;
                    break;
                }
            }
        }

        return found;
    }

    /**
     * {@inheritDoc}
     */
    public void clear()
    {
        if (size > 0)
        {
            Arrays.fill(entries, missingValue);
            size = 0;
        }
    }

    /**
     * Compact the backing arrays by rehashing with a capacity just larger than current size
     * and giving consideration to the load factor.
     */
    public void compact()
    {
        final int idealCapacity = (int)Math.round(size() * (1.0d / loadFactor));
        rehash(findNextPositivePowerOfTwo(Math.max(MIN_CAPACITY, idealCapacity)));
    }

    // ---------------- Boxed Versions Below ----------------

    /**
     * {@inheritDoc}
     */
    public Long get(final Object key)
    {
        return valOrNull(get((long)key));
    }

    /**
     * {@inheritDoc}
     */
    public Long put(final Long key, final Long value)
    {
        return valOrNull(put((long)key, (long)value));
    }

    /**
     * {@inheritDoc}
     */
    public boolean containsKey(final Object key)
    {
        return containsKey((long)key);
    }

    /**
     * {@inheritDoc}
     */
    public boolean containsValue(final Object value)
    {
        return containsValue((long)value);
    }

    /**
     * {@inheritDoc}
     */
    public void putAll(final Map<? extends Long, ? extends Long> map)
    {
        for (final Map.Entry<? extends Long, ? extends Long> entry : map.entrySet())
        {
            put(entry.getKey(), entry.getValue());
        }
    }

    /**
     * {@inheritDoc}
     */
    public KeySet keySet()
    {
        if (null == keySet)
        {
            keySet = new KeySet();
        }

        return keySet;
    }

    /**
     * {@inheritDoc}
     */
    public ValueCollection values()
    {
        if (null == values)
        {
            values = new ValueCollection();
        }

        return values;
    }

    /**
     * {@inheritDoc}
     */
    public EntrySet entrySet()
    {
        if (null == entrySet)
        {
            entrySet = new EntrySet();
        }

        return entrySet;
    }

    /**
     * {@inheritDoc}
     */
    public Long remove(final Object key)
    {
        return valOrNull(remove((long)key));
    }

    public long remove(final long key)
    {
        final long[] entries = this.entries;
        final long missingValue = this.missingValue;
        final int mask = entries.length - 1;
        int keyIndex = Hashing.evenHash(key, mask);

        long oldValue = missingValue;
        while (entries[keyIndex + 1] != missingValue)
        {
            if (entries[keyIndex] == key)
            {
                oldValue = entries[keyIndex + 1];
                entries[keyIndex + 1] = missingValue;
                size--;

                compactChain(keyIndex);

                break;
            }

            keyIndex = next(keyIndex, mask);
        }

        return oldValue;
    }

    @SuppressWarnings("FinalParameters")
    private void compactChain(int deleteKeyIndex)
    {
        final long[] entries = this.entries;
        final long missingValue = this.missingValue;
        final int mask = entries.length - 1;
        int keyIndex = deleteKeyIndex;

        while (true)
        {
            keyIndex = next(keyIndex, mask);
            if (entries[keyIndex + 1] == missingValue)
            {
                break;
            }

            final int hash = Hashing.evenHash(entries[keyIndex], mask);

            if ((keyIndex < hash && (hash <= deleteKeyIndex || deleteKeyIndex <= keyIndex)) ||
                (hash <= deleteKeyIndex && deleteKeyIndex <= keyIndex))
            {
                entries[deleteKeyIndex] = entries[keyIndex];
                entries[deleteKeyIndex + 1] = entries[keyIndex + 1];

                entries[keyIndex + 1] = missingValue;
                deleteKeyIndex = keyIndex;
            }
        }
    }

    /**
     * Get the minimum value stored in the map. If the map is empty then it will return {@link #missingValue()}
     *
     * @return the minimum value stored in the map.
     */
    public long minValue()
    {
        final long missingValue = this.missingValue;
        long min = size == 0 ? missingValue : Long.MAX_VALUE;

        final long[] entries = this.entries;
        final int length = entries.length;

        for (int valueIndex = 1; valueIndex < length; valueIndex += 2)
        {
            final long value = entries[valueIndex];
            if (value != missingValue)
            {
                min = Math.min(min, value);
            }
        }

        return min;
    }

    /**
     * Get the maximum value stored in the map. If the map is empty then it will return {@link #missingValue()}
     *
     * @return the maximum value stored in the map.
     */
    public long maxValue()
    {
        final long missingValue = this.missingValue;
        long max = size == 0 ? missingValue : Long.MIN_VALUE;

        final long[] entries = this.entries;
        final int length = entries.length;

        for (int valueIndex = 1; valueIndex < length; valueIndex += 2)
        {
            final long value = entries[valueIndex];
            if (value != missingValue)
            {
                max = Math.max(max, value);
            }
        }

        return max;
    }

    /**
     * {@inheritDoc}
     */
    public String toString()
    {
        if (isEmpty())
        {
            return "{}";
        }

        final EntryIterator entryIterator = new EntryIterator();
        entryIterator.reset();

        final StringBuilder sb = new StringBuilder().append('{');
        while (true)
        {
            entryIterator.next();
            sb.append(entryIterator.getLongKey()).append('=').append(entryIterator.getLongValue());
            if (!entryIterator.hasNext())
            {
                return sb.append('}').toString();
            }
            sb.append(',').append(' ');
        }
    }

    /**
     * Primitive specialised version of {@link #replace(Object, Object)}
     *
     * @param key key with which the specified value is associated
     * @param value value to be associated with the specified key
     * @return the previous value associated with the specified key, or
     *         {@link #missingValue()} if there was no mapping for the key.
     */
    public long replace(final long key, final long value)
    {
        long currentValue = get(key);
        if (currentValue != missingValue)
        {
            currentValue = put(key, value);
        }

        return currentValue;
    }

    /**
     * Primitive specialised version of {@link #replace(Object, Object, Object)}
     *
     * @param key key with which the specified value is associated
     * @param oldValue value expected to be associated with the specified key
     * @param newValue value to be associated with the specified key
     * @return {@code true} if the value was replaced
     */
    public boolean replace(final long key, final long oldValue, final long newValue)
    {
        final long curValue = get(key);
        if (curValue != oldValue || curValue == missingValue)
        {
            return false;
        }

        put(key, newValue);

        return true;
    }

    /**
     * {@inheritDoc}
     */
    public boolean equals(final Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (!(o instanceof Map))
        {
            return false;
        }

        final Map<?, ?> that = (Map<?, ?>)o;

        return size == that.size() && entrySet().equals(that.entrySet());

    }

    public int hashCode()
    {
        return entrySet().hashCode();
    }

    private static int next(final int index, final int mask)
    {
        return (index + 2) & mask;
    }

    private void capacity(final int newCapacity)
    {
        final int entriesLength = newCapacity * 2;
        if (entriesLength < 0)
        {
            throw new IllegalStateException("max capacity reached at size=" + size);
        }

        resizeThreshold = (int)(newCapacity * loadFactor);
        entries = new long[entriesLength];
        Arrays.fill(entries, missingValue);
    }

    private Long valOrNull(final long value)
    {
        return value == missingValue ? null : value;
    }

    // ---------------- Utility Classes ----------------

    abstract class AbstractIterator implements Serializable
    {
        protected boolean isPositionValid = false;
        private int remaining;
        private int positionCounter;
        private int stopCounter;

        final void reset()
        {
            isPositionValid = false;
            remaining = Long2LongHashMap.this.size;
            final long missingValue = Long2LongHashMap.this.missingValue;
            final long[] entries = Long2LongHashMap.this.entries;
            final int capacity = entries.length;

            int keyIndex = capacity;
            if (entries[capacity - 1] != missingValue)
            {
                keyIndex = 0;
                for (; keyIndex < capacity; keyIndex += 2)
                {
                    if (entries[keyIndex + 1] == missingValue) // lgtm [java/index-out-of-bounds]
                    {
                        break;
                    }
                }
            }

            stopCounter = keyIndex;
            positionCounter = keyIndex + capacity;
        }

        protected final int keyPosition()
        {
            return positionCounter & entries.length - 1;
        }

        public int remaining()
        {
            return remaining;
        }

        public boolean hasNext()
        {
            return remaining > 0;
        }

        protected final void findNext()
        {
            if (!hasNext())
            {
                throw new NoSuchElementException();
            }

            final long[] entries = Long2LongHashMap.this.entries;
            final long missingValue = Long2LongHashMap.this.missingValue;
            final int mask = entries.length - 1;

            for (int keyIndex = positionCounter - 2; keyIndex >= stopCounter; keyIndex -= 2)
            {
                final int index = keyIndex & mask;
                if (entries[index + 1] != missingValue)
                {
                    isPositionValid = true;
                    positionCounter = keyIndex;
                    --remaining;
                    return;
                }
            }

            isPositionValid = false;
            throw new IllegalStateException();
        }

        public void remove()
        {
            if (isPositionValid)
            {
                final int position = keyPosition();
                entries[position + 1] = missingValue;
                --size;

                compactChain(position);

                isPositionValid = false;
            }
            else
            {
                throw new IllegalStateException();
            }
        }
    }

    /**
     * Iterator over keys which supports access to unboxed keys.
     */
    public final class KeyIterator extends AbstractIterator implements Iterator<Long>
    {
        public Long next()
        {
            return nextValue();
        }

        public long nextValue()
        {
            findNext();
            return entries[keyPosition()];
        }
    }

    /**
     * Iterator over values which supports access to unboxed values.
     */
    public final class ValueIterator extends AbstractIterator implements Iterator<Long>
    {
        public Long next()
        {
            return nextValue();
        }

        public long nextValue()
        {
            findNext();
            return entries[keyPosition() + 1];
        }
    }

    /**
     * Iterator over entries which supports access to unboxed keys and values.
     */
    public final class EntryIterator
        extends AbstractIterator
        implements Iterator<Entry<Long, Long>>, Entry<Long, Long>
    {
        public Long getKey()
        {
            return getLongKey();
        }

        public long getLongKey()
        {
            return entries[keyPosition()];
        }

        public Long getValue()
        {
            return getLongValue();
        }

        public long getLongValue()
        {
            return entries[keyPosition() + 1];
        }

        public Long setValue(final Long value)
        {
            return setValue(value.longValue());
        }

        public long setValue(final long value)
        {
            if (!isPositionValid)
            {
                throw new IllegalStateException();
            }

            if (missingValue == value)
            {
                throw new IllegalArgumentException();
            }

            final int keyPosition = keyPosition();
            final long prevValue = entries[keyPosition + 1];
            entries[keyPosition + 1] = value;
            return prevValue;
        }

        public Entry<Long, Long> next()
        {
            findNext();

            if (shouldAvoidAllocation)
            {
                return this;
            }

            return allocateDuplicateEntry();
        }

        private Entry<Long, Long> allocateDuplicateEntry()
        {
            final long k = getLongKey();
            final long v = getLongValue();

            return new Entry<Long, Long>()
            {
                public Long getKey()
                {
                    return k;
                }

                public Long getValue()
                {
                    return v;
                }

                public Long setValue(final Long value)
                {
                    return Long2LongHashMap.this.put(k, value.longValue());
                }

                public int hashCode()
                {
                    return Hashing.hashCode(getLongKey()) ^ Hashing.hashCode(getLongValue());
                }

                public boolean equals(final Object o)
                {
                    if (!(o instanceof Entry))
                    {
                        return false;
                    }

                    final Map.Entry<?, ?> e = (Entry<?, ?>)o;

                    return (e.getKey() != null && e.getValue() != null) &&
                        (e.getKey().equals(k) && e.getValue().equals(v));
                }

                public String toString()
                {
                    return k + "=" + v;
                }
            };
        }

        /**
         * {@inheritDoc}
         */
        public int hashCode()
        {
            return Hashing.hashCode(getLongKey()) ^ Hashing.hashCode(getLongValue());
        }

        /**
         * {@inheritDoc}
         */
        public boolean equals(final Object o)
        {
            if (this == o)
            {
                return true;
            }

            if (!(o instanceof Entry))
            {
                return false;
            }

            final Entry<?, ?> that = (Entry<?, ?>)o;

            return Objects.equals(getKey(), that.getKey()) && Objects.equals(getValue(), that.getValue());
        }
    }

    /**
     * Set of keys which supports optional cached iterators to avoid allocation.
     */
    public final class KeySet extends AbstractSet<Long> implements Serializable
    {
        private final KeyIterator keyIterator = shouldAvoidAllocation ? new KeyIterator() : null;

        /**
         * {@inheritDoc}
         */
        public KeyIterator iterator()
        {
            KeyIterator keyIterator = this.keyIterator;
            if (null == keyIterator)
            {
                keyIterator = new KeyIterator();
            }

            keyIterator.reset();

            return keyIterator;
        }

        /**
         * {@inheritDoc}
         */
        public int size()
        {
            return Long2LongHashMap.this.size();
        }

        /**
         * {@inheritDoc}
         */
        public boolean isEmpty()
        {
            return Long2LongHashMap.this.isEmpty();
        }

        /**
         * {@inheritDoc}
         */
        public void clear()
        {
            Long2LongHashMap.this.clear();
        }

        /**
         * {@inheritDoc}
         */
        public boolean contains(final Object o)
        {
            return contains((long)o);
        }

        public boolean contains(final long key)
        {
            return containsKey(key);
        }
    }

    /**
     * Collection of values which supports optionally cached iterators to avoid allocation.
     */
    public final class ValueCollection extends AbstractCollection<Long>
    {
        private final ValueIterator valueIterator = shouldAvoidAllocation ? new ValueIterator() : null;

        /**
         * {@inheritDoc}
         */
        public ValueIterator iterator()
        {
            ValueIterator valueIterator = this.valueIterator;
            if (null == valueIterator)
            {
                valueIterator = new ValueIterator();
            }

            valueIterator.reset();

            return valueIterator;
        }

        /**
         * {@inheritDoc}
         */
        public int size()
        {
            return Long2LongHashMap.this.size();
        }

        /**
         * {@inheritDoc}
         */
        public boolean contains(final Object o)
        {
            return contains((long)o);
        }

        public boolean contains(final long key)
        {
            return containsValue(key);
        }
    }

    /**
     * Set of entries which supports optionally cached iterators to avoid allocation.
     */
    public final class EntrySet extends AbstractSet<Map.Entry<Long, Long>> implements Serializable
    {
        private final EntryIterator entryIterator = shouldAvoidAllocation ? new EntryIterator() : null;

        /**
         * {@inheritDoc}
         */
        public EntryIterator iterator()
        {
            EntryIterator entryIterator = this.entryIterator;
            if (null == entryIterator)
            {
                entryIterator = new EntryIterator();
            }

            entryIterator.reset();

            return entryIterator;
        }

        /**
         * {@inheritDoc}
         */
        public int size()
        {
            return Long2LongHashMap.this.size();
        }

        /**
         * {@inheritDoc}
         */
        public boolean isEmpty()
        {
            return Long2LongHashMap.this.isEmpty();
        }

        /**
         * {@inheritDoc}
         */
        public void clear()
        {
            Long2LongHashMap.this.clear();
        }

        /**
         * {@inheritDoc}
         */
        public boolean contains(final Object o)
        {
            if (!(o instanceof Entry))
            {
                return false;
            }
            final Entry<?, ?> entry = (Entry<?, ?>)o;
            final Long value = get(entry.getKey());

            return value != null && value.equals(entry.getValue());
        }
    }
}
