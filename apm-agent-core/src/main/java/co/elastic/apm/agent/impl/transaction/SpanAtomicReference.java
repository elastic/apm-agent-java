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

import javax.annotation.Nullable;

class SpanAtomicReference<V extends AbstractSpanImpl<?>> {

    @Nullable
    private V ref;

    private final Object LOCK = new Object();

    /**
     * Increments the referenced {@link AbstractSpanImpl} reference count and returns it. The returned span is guaranteed not to be recycled.
     * It is then the responsibility of the caller to decrement the ref count appropriately.
     * @return the referenced {@link AbstractSpanImpl} with reference count incremented, or {@code null} if no span is referenced
     */
    @Nullable
    public V incrementReferencesAndGet() {
        synchronized (LOCK) {
            if (ref != null) {
                ref.incrementReferences();
            }
            return ref;
        }
    }

    /**
     * Atomically sets the value to {@code newValue} if the current value {@code == expectedValue}.
     *
     * @param expectedValue the expected value
     * @param newValue the new value
     * @return {@code true} if successful. False return indicates that
     * the actual value was not equal to the expected value.
     */
    public boolean compareAndSet(@Nullable V expectedValue, @Nullable V newValue) {
        synchronized (LOCK) {
            if (expectedValue == ref) {
                ref = newValue;
                if (newValue != null) {
                    newValue.incrementReferences();
                }
                if (expectedValue != null) {
                    expectedValue.decrementReferences();
                }
                return true;
            }
            return false;
        }
    }

    public void reset() {
        synchronized (LOCK) {
            if (ref != null) {
                ref.decrementReferences();
                ref = null;
            }
        }
    }
}
