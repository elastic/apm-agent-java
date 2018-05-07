/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 the original author or authors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.objectpool.impl;

import co.elastic.apm.objectpool.Recyclable;
import co.elastic.apm.objectpool.RecyclableObjectFactory;

import javax.annotation.Nullable;

public class ThreadLocalObjectPool<T extends Recyclable> extends AbstractObjectPool<T> {

    private final ThreadLocal<FixedSizeStack<T>> objectPool = new ThreadLocal<>();
    private final int maxNumPooledObjectsPerThread;
    private final boolean preAllocate;

    public ThreadLocalObjectPool(final int maxNumPooledObjectsPerThread, final boolean preAllocate, final RecyclableObjectFactory<T> recyclableObjectFactory) {
        super(recyclableObjectFactory);
        this.maxNumPooledObjectsPerThread = maxNumPooledObjectsPerThread;
        this.preAllocate = preAllocate;
    }

    @Override
    @Nullable
    public T tryCreateInstance() {
        return getStack().pop();
    }

    @Override
    public void recycle(T obj) {
        obj.resetState();
        getStack().push(obj);
    }

    @Override
    public int getObjectsInPool() {
        return getStack().size();
    }

    @Override
    public void close() {
        // only removes the entry of the current thread
        // this could lead to class loader leaks
        objectPool.remove();
    }

    @Override
    public int getSize() {
        return maxNumPooledObjectsPerThread;
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
                stack.push(recyclableObjectFactory.createInstance());
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

        boolean push(T obj) {
            int newTop = top + 1;
            if (newTop >= stack.length) {
                return false;
            }
            stack[newTop] = obj;
            top = newTop;
            return true;
        }

        @Nullable
        T pop() {
            if (top < 0) return null;
            T obj = stack[top--];
            stack[top + 1] = null;
            return obj;
        }

        int size() {
            return top + 1;
        }
    }
}
