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
package co.elastic.apm.agent.sdk.state;

import net.bytebuddy.asm.Advice;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A utility that makes it easy to detect nested method calls.
 */
public class CallDepth {
    private static final ConcurrentMap<String, CallDepth> registry = new ConcurrentHashMap<>();
    private final ThreadLocal<Integer> callDepthPerThread = new ThreadLocal<Integer>();

    private CallDepth() {
    }

    /**
     * Returns or creates a globally shared call depth instance, based on the advice's class name.
     *
     * @param adviceClass the class of the advice the call depth is used in.
     * @return a globally shared call depth instance, based on the advice's class name.
     * @see GlobalVariables
     */
    public static CallDepth get(Class<?> adviceClass) {
        // we want to return the same CallDepth instance even if the advice class has been loaded from different class loaders
        String key = adviceClass.getName();
        return get(key);
    }

    public static CallDepth get(String key) {
        CallDepth callDepth = registry.get(key);
        if (callDepth == null) {
            registry.putIfAbsent(key, new CallDepth());
            callDepth = registry.get(key);
        }
        return callDepth;
    }

    static void clearRegistry() {
        registry.clear();
    }

    /**
     * Gets and increments the call depth counter.
     * Returns {@code 0} if this is the outer-most (non-nested) invocation.
     *
     * @return the call depth before it has been incremented
     */
    public int increment() {
        int depth = get();
        set(depth + 1);
        return depth;
    }

    /**
     * Calls {@link #increment()} and returns {@code false} if this is the outer-most (non-nested) invocation.
     *
     * @return {@code false} if this is the outer-most (non-nested) invocation, {@code true} otherwise
     */
    public boolean isNestedCallAndIncrement() {
        return increment() != 0;
    }

    /**
     * Decrements and gets the call depth counter.
     * Returns {@code 0} if this is the outer-most (non-nested) invocation.
     * <p>
     * Note: this should be the first thing called on exit advices.
     * Also make sure to set {@link Advice.OnMethodExit#onThrowable()} to {@link Throwable}{@code .class}.
     * This ensures we don't end up with inconsistent counts.
     * </p>
     *
     * @return the call depth after it has been incremented
     */
    public int decrement() {
        int depth = get() - 1;
        set(depth);
        assert depth >= 0;
        return depth;
    }

    /**
     * Calls {@link #decrement()} and returns {@code false} if this is the outer-most (non-nested) invocation.
     * <p>
     * Note: this should be the first thing called on exit advices.
     * Also make sure to set {@link Advice.OnMethodExit#onThrowable()} to {@link Throwable}{@code .class}.
     * This ensures we don't end up with inconsistent counts.
     * </p>
     *
     * @return {@code false} if this is the outer-most (non-nested) invocation, {@code true} otherwise
     */
    public boolean isNestedCallAndDecrement() {
        return decrement() != 0;
    }

    public int get() {
        Integer callDepthForCurrentThread = callDepthPerThread.get();
        if (callDepthForCurrentThread == null) {
            callDepthForCurrentThread = 0;
            callDepthPerThread.set(callDepthForCurrentThread);
        }
        return callDepthForCurrentThread;
    }

    private void set(int depth) {
        callDepthPerThread.set(depth);
    }
}
