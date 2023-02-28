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
package co.elastic.apm.agent.objectpool.impl;

import co.elastic.apm.agent.objectpool.Resetter;
import co.elastic.apm.agent.tracer.pooling.Allocator;
import co.elastic.apm.agent.tracer.pooling.Recyclable;

import javax.annotation.Nullable;

// this implementation has been moved to 'benchmarks' module because it is not used in production code (yet)
public class ThreadLocalObjectPool<T extends Recyclable> extends AbstractObjectPool<T> {

    private final ThreadLocal<FixedSizeStack<T>> objectPool = new ThreadLocal<>();
    private final int maxNumPooledObjectsPerThread;
    private final boolean preAllocate;

    public ThreadLocalObjectPool(int maxNumPooledObjectsPerThread, boolean preAllocate, Allocator<T> allocator) {
        super(allocator, Resetter.ForRecyclable.get());
        this.maxNumPooledObjectsPerThread = maxNumPooledObjectsPerThread;
        this.preAllocate = preAllocate;
    }

    @Override
    @Nullable
    public T tryCreateInstance() {
        return getStack().pop();
    }

    @Override
    protected boolean returnToPool(T obj) {
        return getStack().push(obj);
    }

    @Override
    public int getObjectsInPool() {
        return getStack().size();
    }

    @Override
    public void clear() {
    }

    private FixedSizeStack<T> getStack() {
        FixedSizeStack<T> stack = objectPool.get();
        if (stack == null) {
            stack = createStack(preAllocate);
            objectPool.set(stack);
        }
        return stack;
    }

    private FixedSizeStack<T> createStack(boolean preAllocate) {
        FixedSizeStack<T> stack = new FixedSizeStack<>(maxNumPooledObjectsPerThread);
        if (preAllocate) {
            for (int i = 0; i < maxNumPooledObjectsPerThread; i++) {
                stack.push(allocator.createInstance());
            }
        }
        return stack;
    }

    // inspired by https://stackoverflow.com/questions/7727919/creating-a-fixed-size-stack/7728703#7728703
    public static class FixedSizeStack<T> {
        private final T[] stack;
        private int top;

        FixedSizeStack(int maxSize) {
            this.stack = (T[]) new Object[maxSize];
            this.top = -1;
        }

        /**
         * Adds an object on top of the stack
         *
         * @param obj object to push to stack
         * @return {@code true} if object has been added to stack, {@code false} if maximum capacity has been reached
         */
        boolean push(T obj) {
            int newTop = top + 1;
            if (newTop >= stack.length) {
                return false;
            }
            stack[newTop] = obj;
            top = newTop;
            return true;
        }

        /**
         * Removes object from top of the stack
         *
         * @return object on top of stack (if any), {@code null} otherwise
         */
        @Nullable
        T pop() {
            if (top < 0) return null;
            T obj = stack[top--];
            stack[top + 1] = null;
            return obj;
        }

        /**
         * Get stack size
         *
         * @return stack size
         */
        int size() {
            return top + 1;
        }
    }
}
