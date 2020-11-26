package co.elastic.apm.agent.impl.context;

import co.elastic.apm.agent.objectpool.Allocator;
import co.elastic.apm.agent.objectpool.ObjectPool;
import co.elastic.apm.agent.objectpool.Recyclable;
import co.elastic.apm.agent.objectpool.Resetter;
import co.elastic.apm.agent.objectpool.impl.QueueBasedObjectPool;
import co.elastic.apm.agent.report.serialize.DslJsonSerializer;
import org.jctools.queues.atomic.MpmcAtomicArrayQueue;

import javax.annotation.Nullable;
import java.nio.Buffer;
import java.nio.CharBuffer;

public class PooledBuffer implements Recyclable {

    private static final ObjectPool<CharBuffer> charBufferPool = QueueBasedObjectPool.of(new MpmcAtomicArrayQueue<CharBuffer>(128), false,
        new Allocator<CharBuffer>() {
            @Override
            public CharBuffer createInstance() {
                return CharBuffer.allocate(DslJsonSerializer.MAX_LONG_STRING_VALUE_LENGTH);
            }
        },
        new Resetter<CharBuffer>() {
            @Override
            public void recycle(CharBuffer object) {
                ((Buffer) object).clear();
            }
        });

    @Nullable
    private CharBuffer buffer;

    private boolean bufferWriteFinished;

    @Nullable
    private String stringContent;

    private final WriteOperation write;

    public PooledBuffer() {
        write = new WriteOperation(this);
    }

    public PooledBuffer write(String content) {
        this.stringContent = content;
        return this;
    }

    public WriteOperation startWrite() {
        return write;
    }

    public static final class WriteOperation {

        private final PooledBuffer pooledBuffer;

        private WriteOperation(PooledBuffer pooledBuffer) {
            this.pooledBuffer = pooledBuffer;
        }

        private CharBuffer getBuffer(){
            return pooledBuffer.buffer;
        }

        public WriteOperation append(CharSequence s) {
            getBuffer().append(s);
            return this;
        }

        public PooledBuffer endWrite() {
            ((Buffer) getBuffer()).flip();
            return pooledBuffer;
        }
    }



    CharSequence asCharSequence() {
        if (stringContent != null) {
            return stringContent;
        }
        return bufferWriteFinished ? buffer : null;
    }

    // TODO : copyOf


    @Override
    public void resetState() {
        this.stringContent = null;
        if (buffer != null) {
            charBufferPool.recycle(buffer);
            buffer.clear();
        }
        this.bufferWriteFinished = false;

    }
}
