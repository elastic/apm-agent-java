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

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A utility that makes it easy to detect nested method calls.
 */
public class CallDepth {
    private static final ThreadLocal<Map<Class<?>, AtomicInteger>> callDepthPerThread = new ThreadLocal<Map<Class<?>, AtomicInteger>>();

    /**
     * Gets and increments the call depth counter.
     * Returns {@code 0} if this is the outer-most (non-nested) invocation.
     *
     * @param clazz the class for which the call depth should be counted.
     *              Used as a key to distinguish multiple counters for a thread.
     * @return the call depth before it has been incremented
     */
    public static int increment(Class<?> clazz) {
        Map<Class<?>, AtomicInteger> callDepthForCurrentThread = callDepthPerThread.get();
        if (callDepthForCurrentThread == null) {
            callDepthForCurrentThread = new WeakHashMap<Class<?>, AtomicInteger>();
            callDepthPerThread.set(callDepthForCurrentThread);
        }
        AtomicInteger depth = callDepthForCurrentThread.get(clazz);
        if (depth == null) {
            depth = new AtomicInteger();
            callDepthForCurrentThread.put(clazz, depth);
        }
        return depth.getAndIncrement();
    }

    /**
     * Decrements and gets the call depth counter.
     * Returns {@code 0} if this is the outer-most (non-nested) invocation.
     *
     * @param clazz the class for which the call depth should be counted.
     *              Used as a key to distinguish multiple counters for a thread.
     * @return the call depth after it has been incremented
     */
    public static int decrement(Class<?> clazz) {
        int depth = callDepthPerThread.get().get(clazz).decrementAndGet();
        assert depth >= 0;
        return depth;
    }
}
