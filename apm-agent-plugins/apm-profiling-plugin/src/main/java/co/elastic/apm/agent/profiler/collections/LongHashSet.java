/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2021 Elastic and contributors
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
import java.lang.reflect.Array;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

import static co.elastic.apm.agent.profiler.collections.CollectionUtil.findNextPositivePowerOfTwo;
import static co.elastic.apm.agent.profiler.collections.CollectionUtil.validateLoadFactor;

/**
 * Open-addressing with linear-probing expandable hash set. Allocation free in steady state use when expanded.
 * <p>
 * By storing elements as long primitives this significantly reduces memory consumption compared with Java's builtin
 * <code>HashSet&lt;Long&gt;</code>. It implements <code>Set&lt;Long&gt;</code> for convenience, but calling
 * functionality via those methods can add boxing overhead to your usage.
 * <p>
 * This class is not Threadsafe.
 * <p>
 * This HashSet caches its iterator object by default, so nested iteration is not supported. You can override this
 * behaviour at construction by indicating that the iterator should not be cached.
 *
 * @see LongIterator
 * @see Set
 */
public class LongHashSet extends AbstractSet<Long> implements Serializable
{
    /**
     * The initial capacity used when none is specified in the constructor.
     */
    public static final int DEFAULT_INITIAL_CAPACITY = 8;

    static final long MISSING_VALUE = -1;

    private final boolean shouldAvoidAllocation;
    private boolean containsMissingValue;
    private final float loadFactor;
    private int resizeThreshold;
    // NB: excludes missing value
    private int sizeOfArrayValues;

    private long[] values;
    private LongIterator iterator;

    /**
     * Construct a hash set with {@link #DEFAULT_INITIAL_CAPACITY}, {@link Hashing#DEFAULT_LOAD_FACTOR},
     * and iterator caching support.
     */
    public LongHashSet()
    {
        this(DEFAULT_INITIAL_CAPACITY);
    }

    /**
     * Construct a hash set with a proposed capacity, {@link Hashing#DEFAULT_LOAD_FACTOR},
     * and iterator caching support.
     *
     * @param proposedCapacity for the initial capacity of the set.
     */
    public LongHashSet(
        final int proposedCapacity)
    {
        this(proposedCapacity, Hashing.DEFAULT_LOAD_FACTOR, true);
    }

    /**
     * Construct a hash set with a proposed initial capacity, load factor, and iterator caching support.
     *
     * @param proposedCapacity for the initial capacity of the set.
     * @param loadFactor       to be used for resizing.
     */
    public LongHashSet(
        final int proposedCapacity,
        final float loadFactor)
    {
        this(proposedCapacity, loadFactor, true);
    }

    /**
     * Construct a hash set with a proposed initial capacity, load factor, and indicated iterator caching support.
     *
     * @param proposedCapacity      for the initial capacity of the set.
     * @param loadFactor            to be used for resizing.
     * @param shouldAvoidAllocation should the iterator be cached to avoid further allocation.
     */
    public LongHashSet(
        final int proposedCapacity,
        final float loadFactor,
        final boolean shouldAvoidAllocation)
    {
        validateLoadFactor(loadFactor);

        this.shouldAvoidAllocation = shouldAvoidAllocation;
        this.loadFactor = loadFactor;
        sizeOfArrayValues = 0;
        final int capacity = findNextPositivePowerOfTwo(Math.max(DEFAULT_INITIAL_CAPACITY, proposedCapacity));
        resizeThreshold = (int)(capacity * loadFactor); // @DoNotSub
        values = new long[capacity];
        Arrays.fill(values, MISSING_VALUE);
    }

    /**
     * Get the load factor beyond which the set will increase size.
     *
     * @return load factor for when the set should increase size.
     */
    public float loadFactor()
    {
        return loadFactor;
    }

    /**
     * Get the total capacity for the set to which the load factor with be a fraction of.
     *
     * @return the total capacity for the set.
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
    public boolean add(final Long value)
    {
        return add(value.longValue());
    }

    /**
     * Primitive specialised overload of {this#add(Long)}
     *
     * @param value the value to add
     * @return true if the collection has changed, false otherwise
     * @throws IllegalArgumentException if value is missingValue
     */
    public boolean add(final long value)
    {
        if (value == MISSING_VALUE)
        {
            final boolean previousContainsMissingValue = this.containsMissingValue;
            containsMissingValue = true;
            return !previousContainsMissingValue;
        }

        final long[] values = this.values;
        final int mask = values.length - 1;
        int index = Hashing.hash(value, mask);

        while (values[index] != MISSING_VALUE)
        {
            if (values[index] == value)
            {
                return false;
            }

            index = next(index, mask);
        }

        values[index] = value;
        sizeOfArrayValues++;

        if (sizeOfArrayValues > resizeThreshold)
        {
            increaseCapacity();
        }

        return true;
    }

    private void increaseCapacity()
    {
        final int newCapacity = values.length * 2;
        if (newCapacity < 0)
        {
            throw new IllegalStateException("max capacity reached at size=" + size());
        }

        rehash(newCapacity);
    }

    private void rehash(final int newCapacity)
    {
        final int capacity = newCapacity;
        final int mask = newCapacity - 1;
        resizeThreshold = (int)(newCapacity * loadFactor); // @DoNotSub

        final long[] tempValues = new long[capacity];
        Arrays.fill(tempValues, MISSING_VALUE);

        for (final long value : values)
        {
            if (value != MISSING_VALUE)
            {
                int newHash = Hashing.hash(value, mask);
                while (tempValues[newHash] != MISSING_VALUE)
                {
                    newHash = ++newHash & mask;
                }

                tempValues[newHash] = value;
            }
        }

        values = tempValues;
    }

    /**
     * {@inheritDoc}
     */
    public boolean remove(final Object value)
    {
        return value instanceof Long && remove(((Long)value).longValue());
    }

    /**
     * An long specialised version of {this#remove(Object)}.
     *
     * @param value the value to remove
     * @return true if the value was present, false otherwise
     */
    public boolean remove(final long value)
    {
        if (value == MISSING_VALUE)
        {
            final boolean previousContainsMissingValue = this.containsMissingValue;
            containsMissingValue = false;
            return previousContainsMissingValue;
        }

        final long[] values = this.values;
        final int mask = values.length - 1;
        int index = Hashing.hash(value, mask);

        while (values[index] != MISSING_VALUE)
        {
            if (values[index] == value)
            {
                values[index] = MISSING_VALUE;
                compactChain(index);
                sizeOfArrayValues--;
                return true;
            }

            index = next(index, mask);
        }

        return false;
    }

    private static int next(final int index, final int mask)
    {
        return (index + 1) & mask;
    }

    @SuppressWarnings("FinalParameters")
    void compactChain(int deleteIndex)
    {
        final long[] values = this.values;
        final int mask = values.length - 1;

        int index = deleteIndex;
        while (true)
        {
            index = next(index, mask);
            if (values[index] == MISSING_VALUE)
            {
                return;
            }

            final int hash = Hashing.hash(values[index], mask);

            if ((index < hash && (hash <= deleteIndex || deleteIndex <= index)) ||
                (hash <= deleteIndex && deleteIndex <= index))
            {
                values[deleteIndex] = values[index];

                values[index] = MISSING_VALUE;
                deleteIndex = index;
            }
        }
    }

    /**
     * Compact the backing arrays by rehashing with a capacity just larger than current size
     * and giving consideration to the load factor.
     */
    public void compact()
    {
        final int idealCapacity = (int)Math.round(size() * (1.0 / loadFactor));
        rehash(findNextPositivePowerOfTwo(Math.max(DEFAULT_INITIAL_CAPACITY, idealCapacity)));
    }

    /**
     * {@inheritDoc}
     */
    public boolean contains(final Object value)
    {
        return value instanceof Long && contains(((Long)value).longValue());
    }

    /**
     * Contains method that does not box values.
     *
     * @param value to be check for if the set contains it.
     * @return true if the value is contained in the set otherwise false.
     * @see Collection#contains(Object)
     */
    public boolean contains(final long value)
    {
        if (value == MISSING_VALUE)
        {
            return containsMissingValue;
        }

        final long[] values = this.values;
        final int mask = values.length - 1;
        int index = Hashing.hash(value, mask);

        while (values[index] != MISSING_VALUE)
        {
            if (values[index] == value)
            {
                return true;
            }

            index = next(index, mask);
        }

        return false;
    }

    /**
     * {@inheritDoc}
     */
    public int size()
    {
        return sizeOfArrayValues + (containsMissingValue ? 1 : 0);
    }

    /**
     * {@inheritDoc}
     */
    public boolean isEmpty()
    {
        return size() == 0;
    }

    /**
     * {@inheritDoc}
     */
    public void clear()
    {
        if (size() > 0)
        {
            Arrays.fill(values, MISSING_VALUE);
            sizeOfArrayValues = 0;
            containsMissingValue = false;
        }
    }

    /**
     * {@inheritDoc}
     */
    public boolean addAll(final Collection<? extends Long> coll)
    {
        boolean added = false;

        for (final Long value : coll)
        {
            added |= add(value);
        }

        return added;
    }

    /**
     * Alias for {@link #addAll(Collection)} for the specialized case when adding another LongHashSet,
     * avoids boxing and allocations
     *
     * @param coll containing the values to be added.
     * @return {@code true} if this set changed as a result of the call
     */
    public boolean addAll(final LongHashSet coll)
    {
        boolean acc = false;

        for (final long value : coll.values)
        {
            if (value != MISSING_VALUE)
            {
                acc |= add(value);
            }
        }

        if (coll.containsMissingValue)
        {
            acc |= add(MISSING_VALUE);
        }

        return acc;
    }

    /**
     * LongHashSet specialised variant of {this#containsAll(Collection)}.
     *
     * @param other long hash set to compare against.
     * @return true if every element in other is in this.
     */
    public boolean containsAll(final LongHashSet other)
    {
        for (final long value : other.values)
        {
            if (value != MISSING_VALUE && !contains(value))
            {
                return false;
            }
        }

        return !other.containsMissingValue || this.containsMissingValue;
    }

    /**
     * Fast Path set difference for comparison with another LongHashSet.
     * <p>
     * <b>Note:</b> garbage free in the identical case, allocates otherwise.
     *
     * @param other the other set to subtract
     * @return null if identical, otherwise the set of differences
     */
    public LongHashSet difference(final LongHashSet other)
    {
        LongHashSet difference = null;

        for (final long value : values)
        {
            if (value != MISSING_VALUE && !other.contains(value))
            {
                if (difference == null)
                {
                    difference = new LongHashSet();
                }

                difference.add(value);
            }
        }

        if (other.containsMissingValue && !this.containsMissingValue)
        {
            if (difference == null)
            {
                difference = new LongHashSet();
            }

            difference.add(MISSING_VALUE);
        }

        return difference;
    }

    /**
     * {@inheritDoc}
     */
    public boolean removeAll(final Collection<?> coll)
    {
        boolean removed = false;

        for (final Object value : coll)
        {
            removed |= remove(value);
        }

        return removed;
    }

    /**
     * Alias for {@link #removeAll(Collection)} for the specialized case when removing another LongHashSet,
     * avoids boxing and allocations
     *
     * @param coll containing the values to be removed.
     * @return {@code true} if this set changed as a result of the call
     */
    public boolean removeAll(final LongHashSet coll)
    {
        boolean acc = false;

        for (final long value : coll.values)
        {
            if (value != MISSING_VALUE)
            {
                acc |= remove(value);
            }
        }

        if (coll.containsMissingValue)
        {
            acc |= remove(MISSING_VALUE);
        }

        return acc;
    }

    /**
     * {@inheritDoc}
     */
    public LongIterator iterator()
    {
        LongIterator iterator = this.iterator;
        if (null == iterator)
        {
            iterator = new LongIterator();
            if (shouldAvoidAllocation)
            {
                this.iterator = iterator;
            }
        }

        return iterator.reset();
    }

    public void copy(final LongHashSet that)
    {
        if (this.values.length != that.values.length)
        {
            throw new IllegalArgumentException("cannot copy object: masks not equal");
        }

        System.arraycopy(that.values, 0, this.values, 0, this.values.length);
        this.sizeOfArrayValues = that.sizeOfArrayValues;
        this.containsMissingValue = that.containsMissingValue;
    }

    /**
     * {@inheritDoc}
     */
    public String toString()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append('{');

        for (final long value : values)
        {
            if (value != MISSING_VALUE)
            {
                sb.append(value).append(", ");
            }
        }

        if (containsMissingValue)
        {
            sb.append(MISSING_VALUE).append(", ");
        }

        if (sb.length() > 1)
        {
            sb.setLength(sb.length() - 2);
        }

        sb.append('}');

        return sb.toString();
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    public <T> T[] toArray(final T[] a)
    {
        final Class<?> componentType = a.getClass().getComponentType();
        if (!componentType.isAssignableFrom(Long.class))
        {
            throw new ArrayStoreException("cannot store Longs in array of type " + componentType);
        }

        final int size = size();
        final T[] arrayCopy = a.length >= size ? a : (T[])Array.newInstance(componentType, size);
        copyValues(arrayCopy);

        return arrayCopy;
    }

    /**
     * {@inheritDoc}
     */
    public Object[] toArray()
    {
        final Object[] arrayCopy = new Object[size()];
        copyValues(arrayCopy);

        return arrayCopy;
    }

    private void copyValues(final Object[] arrayCopy)
    {
        int i = 0;
        final long[] values = this.values;
        for (final long value : values)
        {
            if (MISSING_VALUE != value)
            {
                arrayCopy[i++] = value;
            }
        }

        if (containsMissingValue)
        {
            arrayCopy[sizeOfArrayValues] = MISSING_VALUE;
        }
    }

    /**
     * {@inheritDoc}
     */
    public boolean equals(final Object other)
    {
        if (other == this)
        {
            return true;
        }

        if (other instanceof LongHashSet)
        {
            final LongHashSet otherSet = (LongHashSet)other;

            return otherSet.containsMissingValue == containsMissingValue &&
                otherSet.sizeOfArrayValues == sizeOfArrayValues &&
                containsAll(otherSet);
        }

        if (!(other instanceof Set))
        {
            return false;
        }

        final Set<?> c = (Set<?>)other;
        if (c.size() != size())
        {
            return false;
        }

        try
        {
            return containsAll(c);
        }
        catch (final ClassCastException | NullPointerException ignore)
        {
            return false;
        }
    }

    /**
     * {@inheritDoc}
     */
    public int hashCode()
    {
        int hashCode = 0;
        for (final long value : values)
        {
            if (value != MISSING_VALUE)
            {
                hashCode += Hashing.hash(value);
            }
        }

        if (containsMissingValue)
        {
            hashCode += Hashing.hash(MISSING_VALUE);
        }

        return hashCode;
    }

    /**
     * Iterator which supports unboxed access to values.
     */
    public final class LongIterator implements Iterator<Long>, Serializable
    {
        private int remaining;
        private int positionCounter;
        private int stopCounter;
        private boolean isPositionValid = false;

        LongIterator reset()
        {
            remaining = size();

            final long[] values = LongHashSet.this.values;
            final int length = values.length;
            int i = length;

            if (values[length - 1] != LongHashSet.MISSING_VALUE)
            {
                for (i = 0; i < length; i++)
                {
                    if (values[i] == LongHashSet.MISSING_VALUE)
                    {
                        break;
                    }
                }
            }

            stopCounter = i;
            positionCounter = i + length;
            isPositionValid = false;

            return this;
        }

        public boolean hasNext()
        {
            return remaining > 0;
        }

        public int remaining()
        {
            return remaining;
        }

        public Long next()
        {
            return nextValue();
        }

        /**
         * Strongly typed alternative of {@link Iterator#next()} to avoid boxing.
         *
         * @return the next long value.
         */
        public long nextValue()
        {
            if (remaining == 1 && containsMissingValue)
            {
                remaining = 0;
                isPositionValid = true;

                return LongHashSet.MISSING_VALUE;
            }

            findNext();

            final long[] values = LongHashSet.this.values;

            return values[position(values)];
        }

        public void remove()
        {
            if (isPositionValid)
            {
                if (0 == remaining && containsMissingValue)
                {
                    containsMissingValue = false;
                }
                else
                {
                    final long[] values = LongHashSet.this.values;
                    final int position = position(values);
                    values[position] = MISSING_VALUE;
                    --sizeOfArrayValues;

                    compactChain(position);
                }

                isPositionValid = false;
            }
            else
            {
                throw new IllegalStateException();
            }
        }

        private void findNext()
        {
            final long[] values = LongHashSet.this.values;
            final int mask = values.length - 1;
            isPositionValid = true;

            for (int i = positionCounter - 1; i >= stopCounter; i--)
            {
                final int index = i & mask;
                if (values[index] != LongHashSet.MISSING_VALUE)
                {
                    positionCounter = i;
                    --remaining;
                    return;
                }
            }

            isPositionValid = false;
            throw new NoSuchElementException();
        }

        private int position(
            final long[] values)
        {
            return positionCounter & (values.length - 1);
        }
    }
}

