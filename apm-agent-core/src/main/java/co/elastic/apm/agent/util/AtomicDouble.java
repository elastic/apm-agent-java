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

import java.util.concurrent.atomic.AtomicLongFieldUpdater;

public class AtomicDouble {

    private volatile long valueBits;

    private static final AtomicLongFieldUpdater<AtomicDouble> valueUpdater =
        AtomicLongFieldUpdater.newUpdater(AtomicDouble.class, "valueBits");

    public AtomicDouble() {
        this(0.0);
    }

    public AtomicDouble(double initialValue) {
        this.valueBits = Double.doubleToLongBits(initialValue);
    }

    public double get() {
        return Double.longBitsToDouble(valueBits);
    }

    public void set(double newValue) {
        valueBits = Double.doubleToLongBits(newValue);
    }

    public boolean compareAndSet(double expected, double newValue) {
        long expectedLong = Double.doubleToLongBits(expected);
        long expectedDouble = Double.doubleToLongBits(newValue);
        return valueUpdater.compareAndSet(this, expectedLong, expectedDouble);
    }

    public double setMax(double value) {
        while (true) {
            double current = get();
            if (current >= value) {
                return current;
            }
            if (compareAndSet(current, value)) {
                return value;
            }
        }
    }

    public double setMin(double value) {
        while (true) {
            double current = get();
            if (current <= value) {
                return current;
            }
            if (compareAndSet(current, value)) {
                return value;
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AtomicDouble that = (AtomicDouble) o;
        return get() == that.get();
    }

    @Override
    public int hashCode() {
        return Double.hashCode(get());
    }
}
