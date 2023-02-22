package co.elastic.apm.plugin.spi;

public class DisabledObjectPool<T> implements ObjectPool<T> {

    private final Allocator<T> allocator;

    public DisabledObjectPool(Allocator<T> allocator) {
        this.allocator = allocator;
    }

    @Override
    public T createInstance() {
        return allocator.createInstance();
    }

    @Override
    public void recycle(T obj) {
    }

    @Override
    public void clear() {
    }
}
