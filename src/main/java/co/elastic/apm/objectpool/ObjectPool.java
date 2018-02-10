package co.elastic.apm.objectpool;

import java.io.Closeable;

public class ObjectPool<T extends Recyclable> implements Closeable {

    private final ThreadLocal<FixedSizeStack<T>> objectPool;
    private final RecyclableObjectFactory<T> recyclableObjectFactory;

    public ObjectPool(int maxNumPooledObjectsPerThread, boolean preAllocate, RecyclableObjectFactory<T> recyclableObjectFactory) {
        this.objectPool = new ThreadLocal<FixedSizeStack<T>>() {
            @Override
            protected FixedSizeStack<T> initialValue() {
                FixedSizeStack<T> stack = new FixedSizeStack<>(maxNumPooledObjectsPerThread);
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

    public T createInstance() {
        T obj = objectPool.get().pop();
        if (obj != null) {
            return obj;
        } else {
            return recyclableObjectFactory.createInstance();
        }
    }

    public void recycle(T obj) {
        obj.resetState();
        objectPool.get().push(obj);
    }

    int getCurrentThreadsQueueSize() {
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
