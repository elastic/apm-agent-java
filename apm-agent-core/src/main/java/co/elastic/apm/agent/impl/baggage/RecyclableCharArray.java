package co.elastic.apm.agent.impl.baggage;

import co.elastic.apm.agent.impl.baggage.otel.PercentEscaper;
import co.elastic.apm.agent.objectpool.ObjectPool;
import co.elastic.apm.agent.objectpool.Resetter;
import co.elastic.apm.agent.objectpool.impl.QueueBasedObjectPool;
import co.elastic.apm.agent.tracer.pooling.Allocator;
import org.jctools.queues.atomic.MpmcAtomicArrayQueue;

/**
 * Loom-Friendly replacement for OTel "TemporaryBuffer" class used by the {@link PercentEscaper}.
 */
public class RecyclableCharArray implements AutoCloseable {

    private static final int POOL_CAPACITY = 32;

    private final char[] buffer = new char[1024];

    private static final Allocator<RecyclableCharArray> ALLOCATOR = new Allocator<RecyclableCharArray>() {
        @Override
        public RecyclableCharArray createInstance() {
            return new RecyclableCharArray();
        }
    };

    private static final Resetter<RecyclableCharArray> RESETTER = new Resetter<RecyclableCharArray>() {
        @Override
        public void recycle(RecyclableCharArray object) {
            //No need to clear array
        }
    };

    private static ObjectPool<RecyclableCharArray> POOL = QueueBasedObjectPool.of(
        new MpmcAtomicArrayQueue<RecyclableCharArray>((POOL_CAPACITY)), false, ALLOCATOR, RESETTER);

    public static RecyclableCharArray acquire() {
        return POOL.createInstance();
    }

    public char[] get() {
        return buffer;
    }

    @Override
    public void close() {
        POOL.recycle(this);
    }

}
