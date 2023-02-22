package co.elastic.apm.plugin.spi;

public class DisabledObjectPoolFactory implements ObjectPoolFactory {

    public static final ObjectPoolFactory INSTANCE = new DisabledObjectPoolFactory();

    private DisabledObjectPoolFactory() {
    }

    @Override
    public <T extends Recyclable> ObjectPool<T> createRecyclableObjectPool(int maxCapacity, Allocator<T> allocator) {
        return new DisabledObjectPool<T>(allocator);
    }
}
