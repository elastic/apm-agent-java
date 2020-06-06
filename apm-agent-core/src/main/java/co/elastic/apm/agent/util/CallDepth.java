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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A utility that makes it easy to detect nested method calls.
 */
public class CallDepth {
    private static final ConcurrentMap<String, CallDepth> registry = new ConcurrentHashMap<>();
    private final ThreadLocal<AtomicInteger> callDepthPerThread = new ThreadLocal<AtomicInteger>();

    public static CallDepth get(Class<?> adviceClass) {
        // we want to return the same CallDepth instance even if the advice class has been loaded from different class loaders
        String key = adviceClass.getName();
        CallDepth callDepth = registry.get(key);
        if (callDepth == null) {
            registry.putIfAbsent(key, new CallDepth());
            callDepth = registry.get(key);
        }
        return callDepth;
    }

    /**
     * Gets and increments the call depth counter.
     * Returns {@code 0} if this is the outer-most (non-nested) invocation.
     *
     * @return the call depth before it has been incremented
     */
    public int increment() {
        AtomicInteger callDepthForCurrentThread = callDepthPerThread.get();
        if (callDepthForCurrentThread == null) {
            callDepthForCurrentThread = new AtomicInteger();
            callDepthPerThread.set(callDepthForCurrentThread);
        }
        return callDepthForCurrentThread.getAndIncrement();
    }

    /**
     * Decrements and gets the call depth counter.
     * Returns {@code 0} if this is the outer-most (non-nested) invocation.
     *
     * @return the call depth after it has been incremented
     */
    public int decrement() {
        int depth = callDepthPerThread.get().decrementAndGet();
        assert depth >= 0;
        return depth;
    }
}
