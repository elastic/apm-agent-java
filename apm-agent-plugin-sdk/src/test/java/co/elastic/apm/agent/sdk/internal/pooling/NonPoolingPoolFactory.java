package co.elastic.apm.agent.sdk.internal.pooling;

import java.util.concurrent.Callable;

public class NonPoolingPoolFactory implements ObjectPooling.ObjectPoolFactory {

    private static class NoopHandle<T> implements ObjectHandle<T> {

        private final T value;

        private NoopHandle(T value) {
            this.value = value;
        }

        @Override
        public T get() {
            return value;
        }

        @Override
        public void close() {
        }
    }

    @Override
    public <T> ObjectPool<NoopHandle<T>> createHandlePool(Callable<T> allocator) {
        return new ObjectPool<NoopHandle<T>>() {
            @Override
            public NoopHandle<T> createInstance() {
                try {
                    return new NoopHandle<>(allocator.call());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }
}
