package co.elastic.apm.objectpool.impl;

import co.elastic.apm.objectpool.ObjectPool;
import co.elastic.apm.objectpool.Recyclable;
import co.elastic.apm.objectpool.RecyclableObjectFactory;
import co.elastic.apm.util.MathUtils;
import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.EventTranslatorOneArg;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.Sequence;
import com.lmax.disruptor.Sequencer;

public class RingBufferObjectPool<T extends Recyclable> extends AbstractObjectPool<T> {

    private final RingBuffer<PooledObjectHolder<T>> ringBuffer;
    private final Sequence sequence;
    private final EventTranslatorOneArg<PooledObjectHolder<T>, T> translator =
        new EventTranslatorOneArg<PooledObjectHolder<T>, T>() {
            @Override
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
        super(recyclableObjectFactory);
        this.ringBuffer = RingBuffer.createMultiProducer(new PooledObjectEventFactory<T>(), MathUtils.getNextPowerOf2(maxPooledElements));
        this.sequence = new Sequence(Sequencer.INITIAL_CURSOR_VALUE);
        this.ringBuffer.addGatingSequences(sequence);
        if (preAllocate) {
            for (int i = 0; i < maxPooledElements; i++) {
                ringBuffer.tryPublishEvent(translator, recyclableObjectFactory.createInstance());
            }
        }
    }

    @Override
    public T tryCreateInstance() {
        long sequence = claimTailSequences(1);
        if (sequence != -1) {
            return getFromBuffer(sequence);
        }
        return null;
    }

    private T getFromBuffer(long sequence) {
        PooledObjectHolder<T> pooledObjectHolder = ringBuffer.get(sequence);
        T value = pooledObjectHolder.value;
        pooledObjectHolder.value = null;
        return value;
    }

    @Override
    public void fillFromOtherPool(ObjectPool<T> otherPool, int maxElements) {
        long sequence = claimTailSequences(maxElements);
        if (sequence != -1) {
            for (int i = 0; i < maxElements; i++) {
                T recyclable = getFromBuffer(sequence - i);
                if (recyclable != null) {
                    otherPool.recycle(recyclable);
                }
            }
        }
    }

    private long claimTailSequences(int n) {
        while (true) {
            final long currentSequence = sequence.get();
            final long nextSequence = currentSequence + n;
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
    public int getObjectsInPool() {
        // as the size of the ring buffer is an int, this can never overflow
        return (int) (ringBuffer.getCursor() - sequence.get());
    }

    @Override
    public void close() {
    }

    @Override
    public int getSize() {
        return ringBuffer.getBufferSize();
    }

    private static class PooledObjectHolder<T> {
        T value;

        public void set(T value) {
            this.value = value;
        }
    }

    private static class PooledObjectEventFactory<T> implements EventFactory<PooledObjectHolder<T>> {
        @Override
        public PooledObjectHolder<T> newInstance() {
            return new PooledObjectHolder<>();
        }
    }
}
