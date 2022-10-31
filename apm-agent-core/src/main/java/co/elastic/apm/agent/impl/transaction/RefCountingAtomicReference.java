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
import java.util.concurrent.atomic.AtomicReference;

public class RefCountingAtomicReference<V extends AbstractSpan<?>> {

    private final AtomicReference<V> delegate = new AtomicReference<>();

    /**
     * Gets the referenced {@link AbstractSpan}. It is the responsibility of the caller to assume that while holding the returned
     * {@link AbstractSpan}, it may be unreferenced by another thread, decrementing its reference count down to zero, thus causing its
     * recycling.
     * @return the referenced {@link AbstractSpan}, or {@code null} if such is not referenced
     */
    @Nullable
    public V get() {
        return delegate.get();
    }

    /**
     * Gets the referenced {@link AbstractSpan} and if not {@code null}, increments its reference count. It is then the responsibility of
     * the caller to decrement the ref count appropriately.
     * @return the referenced {@link AbstractSpan} with referenced incremented, or {@code null} if such is not referenced
     */
    @Nullable
    public V getAndIncrementReferences() {
        V referenced = delegate.get();
        if (referenced != null) {
            referenced.incrementReferences();
        }
        return referenced;
    }

    public boolean compareAndSet(@Nullable V expectedValue, @Nullable V newValue) {
        boolean replaced = delegate.compareAndSet(expectedValue, newValue);
        if (replaced) {
            if (newValue != null) {
                newValue.incrementReferences();
            }
            if (expectedValue != null) {
                expectedValue.decrementReferences();
            }
        }
        return replaced;
    }

    public void reset() {
        V current = delegate.get();
        if (current != null && delegate.compareAndSet(current, null)) {
            current.decrementReferences();
        }
    }
}
