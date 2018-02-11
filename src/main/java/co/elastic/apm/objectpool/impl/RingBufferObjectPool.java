package co.elastic.apm.objectpool.impl;

import co.elastic.apm.objectpool.ObjectPool;
import co.elastic.apm.objectpool.Recyclable;
import co.elastic.apm.objectpool.RecyclableObjectFactory;
import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.EventTranslatorOneArg;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.Sequence;
import com.lmax.disruptor.Sequencer;

import java.util.concurrent.atomic.LongAdder;

public class RingBufferObjectPool<T extends Recyclable> implements ObjectPool<T> {

    private final RecyclableObjectFactory<T> recyclableObjectFactory;
    private final RingBuffer<PooledObjectHolder<T>> ringBuffer;
    private final Sequence sequence;
    private final LongAdder garbageCreated = new LongAdder();
    private final EventTranslatorOneArg<PooledObjectHolder<T>, T> translator =
        new EventTranslatorOneArg<PooledObjectHolder<T>, T>() {
            public void translateTo(PooledObjectHolder<T> event, long sequence, T recyclable) {
                event.set(recyclable);
            }
        };

    /**
     * @param maxPooledElements       must be a power of 2
     * @param preAllocate             when set to true, the recyclableObjectFactory will be used to create maxPooledElements objects
     *                                which are then stored in the ring buffer
     * @param recyclableObjectFactory a factory method which is used to create new instances of the recyclable object. This factory is
     *                                used when there are no objects in the ring buffer and to preallocate the ring buffer.
     */
    public RingBufferObjectPool(int maxPooledElements, boolean preAllocate, RecyclableObjectFactory<T> recyclableObjectFactory) {
        this.ringBuffer = RingBuffer.createMultiProducer(new PooledObjectEventFactory<>(), maxPooledElements);
        this.sequence = new Sequence(Sequencer.INITIAL_CURSOR_VALUE);
        this.ringBuffer.addGatingSequences(sequence);
        this.recyclableObjectFactory = recyclableObjectFactory;
        if (preAllocate) {
            for (int i = 0; i < maxPooledElements; i++) {
                ringBuffer.tryPublishEvent(translator, recyclableObjectFactory.createInstance());
            }
        }
    }

    @Override
    public T createInstance() {
        long sequence = claimTailSequence();
        if (sequence != -1) {
            PooledObjectHolder<T> pooledObjectHolder = ringBuffer.get(sequence);
            try {
                return pooledObjectHolder.value;
            } finally {
                pooledObjectHolder.value = null;
            }
        } else {
            // buffer is empty, falling back to creating a new instance
            garbageCreated.increment();
            return recyclableObjectFactory.createInstance();
        }
    }

    private long claimTailSequence() {
        while (true) {
            final long currentSequence = sequence.get();
            final long nextSequence = currentSequence + 1;
            final long availableSequence = ringBuffer.getCursor();
            if (nextSequence <= availableSequence) {
                if (sequence.compareAndSet(currentSequence, nextSequence)) {
                    return nextSequence;
                }
            } else {
                return -1;
            }
        }
    }

    @Override
    public void recycle(T obj) {
        obj.resetState();
        ringBuffer.tryPublishEvent(translator, obj);
    }

    @Override
    public int getObjectPoolSize() {
        // as the size of the ring buffer is an int, this can never overflow
        return (int) (ringBuffer.getCursor() - sequence.get());
    }

    @Override
    public void close() {
    }

    public long getGarbageCreated() {
        return garbageCreated.longValue();
    }

    private static class PooledObjectHolder<T> {
        T value;

        public void set(T value) {
            this.value = value;
        }
    }

    private static class PooledObjectEventFactory<T> implements EventFactory<PooledObjectHolder<T>> {
        public PooledObjectHolder<T> newInstance() {
            return new PooledObjectHolder<>();
        }
    }
}
