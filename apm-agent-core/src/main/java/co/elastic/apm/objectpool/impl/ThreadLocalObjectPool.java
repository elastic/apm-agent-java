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
import com.blogspot.mydailyjava.weaklockfree.DetachedThreadLocal;

import javax.annotation.Nullable;

public class ThreadLocalObjectPool<T extends Recyclable> extends AbstractObjectPool<T> {

    private final DetachedThreadLocal<FixedSizeStack<T>> objectPool;
    private final int maxNumPooledObjectsPerThread;

    public ThreadLocalObjectPool(final int maxNumPooledObjectsPerThread, final boolean preAllocate, final RecyclableObjectFactory<T> recyclableObjectFactory) {
        super(recyclableObjectFactory);
        this.maxNumPooledObjectsPerThread = maxNumPooledObjectsPerThread;
        this.objectPool = new DetachedThreadLocal<FixedSizeStack<T>>(DetachedThreadLocal.Cleaner.INLINE) {
            @Override
            protected FixedSizeStack<T> initialValue(Thread thread) {
                FixedSizeStack<T> stack = new FixedSizeStack<>(maxNumPooledObjectsPerThread);
                if (preAllocate) {
                    for (int i = 0; i < maxNumPooledObjectsPerThread; i++) {
                        stack.push(recyclableObjectFactory.createInstance());
                    }
                }
                return stack;
            }
        };
    }

    @Override
    @Nullable
    public T tryCreateInstance() {
        return objectPool.get().pop();
    }

    @Override
    public void recycle(T obj) {
        obj.resetState();
        objectPool.get().push(obj);
    }

    @Override
    public int getObjectsInPool() {
        return objectPool.get().size();
    }

    @Override
    public void close() {
        objectPool.clearAll();
    }

    @Override
    public int getSize() {
        return maxNumPooledObjectsPerThread;
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
