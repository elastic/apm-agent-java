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
 * Copyright 2014-2019 Real Logic Ltd.
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
import static java.util.Objects.requireNonNull;


/**
 * {@link Map} implementation specialised for int keys using open addressing and
 * linear probing for cache efficient access.
 *
 * @param <V> type of values stored in the {@link Map}
 */
public class Int2ObjectHashMap<V>
    implements Map<Integer, V>, Serializable
{
    static final int MIN_CAPACITY = 8;

    private final float loadFactor;
    private int resizeThreshold;
    private int size;
    private final boolean shouldAvoidAllocation;

    private int[] keys;
    private Object[] values;

    private ValueCollection valueCollection;
    private KeySet keySet;
    private EntrySet entrySet;

    public Int2ObjectHashMap()
    {
        this(MIN_CAPACITY, Hashing.DEFAULT_LOAD_FACTOR, true);
    }

    public Int2ObjectHashMap(
        final int initialCapacity,
        final float loadFactor)
    {
        this(initialCapacity, loadFactor, true);
    }

    /**
     * Construct a new map allowing a configuration for initial capacity and load factor.
     * @param initialCapacity       for the backing array
     * @param loadFactor            limit for resizing on puts
     * @param shouldAvoidAllocation should allocation be avoided by caching iterators and map entries.
     */
    public Int2ObjectHashMap(
        final int initialCapacity,
        final float loadFactor,
        final boolean shouldAvoidAllocation)
    {
        validateLoadFactor(loadFactor);

        this.loadFactor = loadFactor;
        this.shouldAvoidAllocation = shouldAvoidAllocation;

        /* */ final int capacity = findNextPositivePowerOfTwo(Math.max(MIN_CAPACITY, initialCapacity));
        /* */ resizeThreshold = (int)(capacity * loadFactor);

        keys = new int[capacity];
        values = new Object[capacity];
    }

    /**
     * Copy construct a new map from an existing one.
     *
     * @param mapToCopy for construction.
     */
    public Int2ObjectHashMap(final Int2ObjectHashMap<V> mapToCopy)
    {
        this.loadFactor = mapToCopy.loadFactor;
        this.resizeThreshold = mapToCopy.resizeThreshold;
        this.size = mapToCopy.size;
        this.shouldAvoidAllocation = mapToCopy.shouldAvoidAllocation;

        keys = mapToCopy.keys.clone();
        values = mapToCopy.values.clone();
    }

    /**
     * Get the load factor beyond which the map will increase size.
     *
     * @return load factor for when the map should increase size.
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
        return values.length;
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
        return 0 == size;
    }

    /**
     * {@inheritDoc}
     */
    public boolean containsKey(final Object key)
    {
        return containsKey(((Integer)key).intValue());
    }

    /**
     * Overloaded version of {@link Map#containsKey(Object)} that takes a primitive int key.
     *
     * @param key for indexing the {@link Map}
     * @return true if the key is found otherwise false.
     */
    public boolean containsKey(final int key)
    {
        final int mask = values.length - 1;
        int index = Hashing.hash(key, mask);

        boolean found = false;
        while (null != values[index])
        {
            if (key == keys[index])
            {
                found = true;
                break;
            }

            index = ++index & mask;
        }

        return found;
    }

    /**
     * {@inheritDoc}
     */
    public boolean containsValue(final Object value)
    {
        boolean found = false;
        final Object val = mapNullValue(value);
        if (null != val)
        {
            for (final Object v : values)
            {
                if (val.equals(v))
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
    public V get(final Object key)
    {
        return get(((Integer)key).intValue());
    }

    /**
     * Overloaded version of {@link Map#get(Object)} that takes a primitive int key.
     *
     * @param key for indexing the {@link Map}
     * @return the value if found otherwise null
     */
    public V get(final int key)
    {
        return unmapNullValue(getMapped(key));
    }

    @SuppressWarnings("unchecked")
    protected V getMapped(final int key)
    {
        final int mask = values.length - 1;
        int index = Hashing.hash(key, mask);

        Object value;
        while (null != (value = values[index]))
        {
            if (key == keys[index])
            {
                break;
            }

            index = ++index & mask;
        }

        return (V)value;
    }

    /**
     * {@inheritDoc}
     */
    public V put(final Integer key, final V value)
    {
        return put(key.intValue(), value);
    }

    /**
     * Overloaded version of {@link Map#put(Object, Object)} that takes a primitive int key.
     *
     * @param key   for indexing the {@link Map}
     * @param value to be inserted in the {@link Map}
     * @return the previous value if found otherwise null
     */
    @SuppressWarnings("unchecked")
    public V put(final int key, final V value)
    {
        final V val = (V)mapNullValue(value);
        requireNonNull(val, "value cannot be null");

        V oldValue = null;
        final int mask = values.length - 1;
        int index = Hashing.hash(key, mask);

        while (null != values[index])
        {
            if (key == keys[index])
            {
                oldValue = (V)values[index];
                break;
            }

            index = ++index & mask;
        }

        if (null == oldValue)
        {
            ++size;
            keys[index] = key;
        }

        values[index] = val;

        if (size > resizeThreshold)
        {
            increaseCapacity();
        }

        return unmapNullValue(oldValue);
    }

    /**
     * {@inheritDoc}
     */
    public V remove(final Object key)
    {
        return remove(((Integer)key).intValue());
    }

    /**
     * Overloaded version of {@link Map#remove(Object)} that takes a primitive int key.
     *
     * @param key for indexing the {@link Map}
     * @return the value if found otherwise null
     */
    public V remove(final int key)
    {
        final int mask = values.length - 1;
        int index = Hashing.hash(key, mask);

        Object value;
        while (null != (value = values[index]))
        {
            if (key == keys[index])
            {
                values[index] = null;
                --size;

                compactChain(index);
                break;
            }

            index = ++index & mask;
        }

        return unmapNullValue(value);
    }

    /**
     * {@inheritDoc}
     */
    public void clear()
    {
        if (size > 0)
        {
            Arrays.fill(values, null);
            size = 0;
        }
    }

    /**
     * Compact the {@link Map} backing arrays by rehashing with a capacity just larger than current size
     * and giving consideration to the load factor.
     */
    public void compact()
    {
        final int idealCapacity = (int)Math.round(size() * (1.0d / loadFactor));
        rehash(findNextPositivePowerOfTwo(Math.max(MIN_CAPACITY, idealCapacity)));
    }

    /**
     * {@inheritDoc}
     */
    public void putAll(final Map<? extends Integer, ? extends V> map)
    {
        for (final Entry<? extends Integer, ? extends V> entry : map.entrySet())
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
        if (null == valueCollection)
        {
            valueCollection = new ValueCollection();
        }

        return valueCollection;
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
            sb.append(entryIterator.getIntKey()).append('=').append(unmapNullValue(entryIterator.getValue()));
            if (!entryIterator.hasNext())
            {
                return sb.append('}').toString();
            }
            sb.append(',').append(' ');
        }
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

        if (size != that.size())
        {
            return false;
        }

        for (int i = 0, length = values.length; i < length; i++)
        {
            final Object thisValue = values[i];
            if (null != thisValue)
            {
                final Object thatValue = that.get(keys[i]);
                if (!thisValue.equals(mapNullValue(thatValue)))
                {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * {@inheritDoc}
     */
    public int hashCode()
    {
        int result = 0;

        for (int i = 0, length = values.length; i < length; i++)
        {
            final Object value = values[i];
            if (null != value)
            {
                result += (keys[i] ^ value.hashCode());
            }
        }

        return result;
    }

    protected Object mapNullValue(final Object value)
    {
        return value;
    }

    @SuppressWarnings("unchecked")
    protected V unmapNullValue(final Object value)
    {
        return (V)value;
    }

    /**
     * Primitive specialised version of {@link #replace(Object, Object)}
     *
     * @param key   key with which the specified value is associated
     * @param value value to be associated with the specified key
     * @return the previous value associated with the specified key, or
     * {@code null} if there was no mapping for the key.
     */
    public V replace(final int key, final V value)
    {
        V curValue = get(key);
        if (curValue != null)
        {
            curValue = put(key, value);
        }

        return curValue;
    }

    /**
     * Primitive specialised version of {@link #replace(Object, Object, Object)}
     *
     * @param key      key with which the specified value is associated
     * @param oldValue value expected to be associated with the specified key
     * @param newValue value to be associated with the specified key
     * @return {@code true} if the value was replaced
     */
    public boolean replace(final int key, final V oldValue, final V newValue)
    {
        final Object curValue = get(key);
        if (curValue == null || !Objects.equals(unmapNullValue(curValue), oldValue))
        {
            return false;
        }

        put(key, newValue);

        return true;
    }

    private void increaseCapacity()
    {
        final int newCapacity = values.length << 1;
        if (newCapacity < 0)
        {
            throw new IllegalStateException("max capacity reached at size=" + size);
        }

        rehash(newCapacity);
    }

    private void rehash(final int newCapacity)
    {
        final int mask = newCapacity - 1;
        /* */ resizeThreshold = (int)(newCapacity * loadFactor);

        final int[] tempKeys = new int[newCapacity];
        final Object[] tempValues = new Object[newCapacity];

        for (int i = 0, size = values.length; i < size; i++)
        {
            final Object value = values[i];
            if (null != value)
            {
                final int key = keys[i];
                int index = Hashing.hash(key, mask);
                while (null != tempValues[index])
                {
                    index = ++index & mask;
                }

                tempKeys[index] = key;
                tempValues[index] = value;
            }
        }

        keys = tempKeys;
        values = tempValues;
    }

    @SuppressWarnings("FinalParameters")
    private void compactChain(int deleteIndex)
    {
        final int mask = values.length - 1;
        int index = deleteIndex;
        while (true)
        {
            index = ++index & mask;
            if (null == values[index])
            {
                break;
            }

            final int hash = Hashing.hash(keys[index], mask);

            if ((index < hash && (hash <= deleteIndex || deleteIndex <= index)) ||
                (hash <= deleteIndex && deleteIndex <= index))
            {
                keys[deleteIndex] = keys[index];
                values[deleteIndex] = values[index];

                values[index] = null;
                deleteIndex = index;
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Sets and Collections
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Set of keys which supports optionally cached iterators to avoid allocation.
     */
    public final class KeySet extends AbstractSet<Integer> implements Serializable
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

        public int size()
        {
            return Int2ObjectHashMap.this.size();
        }

        public boolean contains(final Object o)
        {
            return Int2ObjectHashMap.this.containsKey(o);
        }

        public boolean contains(final int key)
        {
            return Int2ObjectHashMap.this.containsKey(key);
        }

        public boolean remove(final Object o)
        {
            return null != Int2ObjectHashMap.this.remove(o);
        }

        public boolean remove(final int key)
        {
            return null != Int2ObjectHashMap.this.remove(key);
        }

        public void clear()
        {
            Int2ObjectHashMap.this.clear();
        }
    }

    /**
     * Collection of values which supports optionally cached iterators to avoid allocation.
     */
    public final class ValueCollection extends AbstractCollection<V> implements Serializable
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

        public int size()
        {
            return Int2ObjectHashMap.this.size();
        }

        public boolean contains(final Object o)
        {
            return Int2ObjectHashMap.this.containsValue(o);
        }

        public void clear()
        {
            Int2ObjectHashMap.this.clear();
        }
    }

    /**
     * Set of entries which supports access via an optionally cached iterator to avoid allocation.
     */
    public final class EntrySet extends AbstractSet<Entry<Integer, V>> implements Serializable
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

        public int size()
        {
            return Int2ObjectHashMap.this.size();
        }

        public void clear()
        {
            Int2ObjectHashMap.this.clear();
        }

        /**
         * {@inheritDoc}
         */
        public boolean contains(final Object o)
        {
            final Entry entry = (Entry)o;
            final int key = (Integer)entry.getKey();
            final V value = getMapped(key);
            return value != null && value.equals(mapNullValue(entry.getValue()));
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Iterators
    ///////////////////////////////////////////////////////////////////////////////////////////////

    abstract class AbstractIterator<T> implements Iterator<T>, Serializable
    {
        private int posCounter;
        private int stopCounter;
        private int remaining;
        boolean isPositionValid = false;

        protected final int position()
        {
            return posCounter & (values.length - 1);
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

            final Object[] values = Int2ObjectHashMap.this.values;
            final int mask = values.length - 1;

            for (int i = posCounter - 1; i >= stopCounter; i--)
            {
                final int index = i & mask;
                if (null != values[index])
                {
                    posCounter = i;
                    isPositionValid = true;
                    --remaining;
                    return;
                }
            }

            isPositionValid = false;
            throw new IllegalStateException();
        }

        public abstract T next();

        public void remove()
        {
            if (isPositionValid)
            {
                final int position = position();
                values[position] = null;
                --size;

                compactChain(position);

                isPositionValid = false;
            }
            else
            {
                throw new IllegalStateException();
            }
        }

        final void reset()
        {
            remaining = Int2ObjectHashMap.this.size;
            final Object[] values = Int2ObjectHashMap.this.values;
            final int capacity = values.length;

            int i = capacity;
            if (null != values[capacity - 1])
            {
                for (i = 0; i < capacity; i++)
                {
                    if (null == values[i])
                    {
                        break;
                    }
                }
            }

            stopCounter = i;
            posCounter = i + capacity;
            isPositionValid = false;
        }
    }

    /**
     * Iterator over values.
     */
    public class ValueIterator extends AbstractIterator<V>
    {
        public V next()
        {
            findNext();

            return unmapNullValue(values[position()]);
        }
    }

    /**
     * Iterator over keys which supports access to unboxed keys.
     */
    public class KeyIterator extends AbstractIterator<Integer>
    {
        public Integer next()
        {
            return nextInt();
        }

        public int nextInt()
        {
            findNext();

            return keys[position()];
        }
    }

    /**
     * Iterator over entries which supports access to unboxed keys and values.
     */
    public class EntryIterator
        extends AbstractIterator<Entry<Integer, V>>
        implements Entry<Integer, V>
    {
        public Entry<Integer, V> next()
        {
            findNext();
            if (shouldAvoidAllocation)
            {
                return this;
            }

            return allocateDuplicateEntry();
        }

        private Entry<Integer, V> allocateDuplicateEntry()
        {
            final int k = getIntKey();
            final V v = getValue();

            return new Entry<Integer, V>()
            {
                public Integer getKey()
                {
                    return k;
                }

                public V getValue()
                {
                    return v;
                }

                public V setValue(final V value)
                {
                    return Int2ObjectHashMap.this.put(k, value);
                }

                public int hashCode()
                {
                    return getIntKey() ^ (v != null ? v.hashCode() : 0);
                }

                public boolean equals(final Object o)
                {
                    if (!(o instanceof Entry))
                    {
                        return false;
                    }

                    final Entry e = (Entry)o;

                    return (e.getKey() != null && e.getKey().equals(k)) &&
                        ((e.getValue() == null && v == null) || e.getValue().equals(v));
                }

                public String toString()
                {
                    return k + "=" + v;
                }
            };
        }

        public Integer getKey()
        {
            return getIntKey();
        }

        public int getIntKey()
        {
            return keys[position()];
        }

        public V getValue()
        {
            return unmapNullValue(values[position()]);
        }

        @SuppressWarnings("unchecked")
        public V setValue(final V value)
        {
            final V val = (V)mapNullValue(value);
            requireNonNull(val, "value cannot be null");

            if (!this.isPositionValid)
            {
                throw new IllegalStateException();
            }

            final int pos = position();
            final Object oldValue = values[pos];
            values[pos] = val;

            return (V)oldValue;
        }
    }
}
