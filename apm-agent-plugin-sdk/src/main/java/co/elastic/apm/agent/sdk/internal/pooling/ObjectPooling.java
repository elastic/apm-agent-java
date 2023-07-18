package co.elastic.apm.agent.sdk.internal.pooling;

import co.elastic.apm.agent.sdk.internal.InternalUtil;

import java.util.concurrent.Callable;

public class ObjectPooling {

    private static final ObjectPoolFactory factory;

    static {
        factory = InternalUtil.getServiceProvider(ObjectPoolFactory.class);
    }

    public static <T> ObjectPool<? extends ObjectHandle<T>> createWithDefaultFactory(Callable<T> allocator) {
        return factory.createHandlePool(allocator);
    }

    public interface ObjectPoolFactory {
        <T> ObjectPool<? extends ObjectHandle<T>> createHandlePool(Callable<T> allocator);
    }
}
