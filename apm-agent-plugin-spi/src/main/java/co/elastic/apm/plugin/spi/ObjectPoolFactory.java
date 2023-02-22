package co.elastic.apm.plugin.spi;

public interface ObjectPoolFactory {

    <T extends Recyclable> ObjectPool<T> createRecyclableObjectPool(int maxCapacity, Allocator<T> allocator);
}
