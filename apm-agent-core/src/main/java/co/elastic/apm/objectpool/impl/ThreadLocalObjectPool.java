package co.elastic.apm.objectpool.impl;

import co.elastic.apm.objectpool.ObjectPool;
import co.elastic.apm.objectpool.Recyclable;
import co.elastic.apm.objectpool.RecyclableObjectFactory;

public class ThreadLocalObjectPool<T extends Recyclable> implements ObjectPool<T> {

    private final ThreadLocal<FixedSizeStack<T>> objectPool;
    private final RecyclableObjectFactory<T> recyclableObjectFactory;

    public ThreadLocalObjectPool(final int maxNumPooledObjectsPerThread, final boolean preAllocate, final RecyclableObjectFactory<T> recyclableObjectFactory) {
        this.objectPool = new ThreadLocal<FixedSizeStack<T>>() {
            @Override
            protected FixedSizeStack<T> initialValue() {
                FixedSizeStack<T> stack = new FixedSizeStack<T>(maxNumPooledObjectsPerThread);
                if (preAllocate) {
                    for (int i = 0; i < maxNumPooledObjectsPerThread; i++) {
                        stack.push(recyclableObjectFactory.createInstance());
                    }
                }
                return stack;
            }
        };
        this.recyclableObjectFactory = recyclableObjectFactory;
    }

    @Override
    public T createInstance() {
        T obj = objectPool.get().pop();
        if (obj != null) {
            return obj;
        } else {
            return recyclableObjectFactory.createInstance();
        }
    }

    @Override
    public void recycle(T obj) {
        obj.resetState();
        objectPool.get().push(obj);
    }

    @Override
    public int getObjectPoolSize() {
        return objectPool.get().size();
    }

    @Override
    public void close() {
        objectPool.remove();
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
