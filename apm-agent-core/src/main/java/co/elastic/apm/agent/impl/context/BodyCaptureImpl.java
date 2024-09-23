/*
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package co.elastic.apm.agent.impl.context;

import co.elastic.apm.agent.objectpool.Resetter;
import co.elastic.apm.agent.objectpool.impl.QueueBasedObjectPool;
import co.elastic.apm.agent.tracer.configuration.WebConfiguration;
import co.elastic.apm.agent.tracer.metadata.BodyCapture;
import co.elastic.apm.agent.tracer.pooling.Allocator;
import co.elastic.apm.agent.tracer.pooling.ObjectPool;
import co.elastic.apm.agent.tracer.pooling.Recyclable;
import org.jctools.queues.atomic.MpmcAtomicArrayQueue;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;

public class BodyCaptureImpl implements BodyCapture, Recyclable {
    private static final ObjectPool<ByteBuffer> BYTE_BUFFER_POOL = QueueBasedObjectPool.of(new MpmcAtomicArrayQueue<ByteBuffer>(128), false,
        new Allocator<ByteBuffer>() {
            @Override
            public ByteBuffer createInstance() {
                return ByteBuffer.allocate(WebConfiguration.MAX_BODY_CAPTURE_BYTES);
            }
        },
        new Resetter<ByteBuffer>() {
            @Override
            public void recycle(ByteBuffer object) {
                object.clear();
            }
        });

    private enum CaptureState {
        NOT_ELIGIBLE, // initial state
        ELIGIBLE, // eligible but before preconditions evaluation
        PRECONDITIONS_PASSED, // post preconditions (passed), can start capture
        PRECONDITIONS_FAILED, // post preconditions (failed), no body will be captured
        STARTED // the body capturing has been started, a buffer was acquired
    }

    private volatile CaptureState state;

    private final StringBuilder charset;

    /**
     * The maximum number of bytes to capture, if the body is longer remaining bytes will be dropped.
     */
    private int numBytesToCapture;

    @Nullable
    private ByteBuffer bodyBuffer;

    BodyCaptureImpl() {
        charset = new StringBuilder();
        resetState();
    }

    @Override
    public void resetState() {
        state = CaptureState.NOT_ELIGIBLE;
        charset.setLength(0);
        if (bodyBuffer != null) {
            BYTE_BUFFER_POOL.recycle(bodyBuffer);
            bodyBuffer = null;
        }
    }

    @Override
    public void markEligibleForCapturing() {
        if (state == CaptureState.NOT_ELIGIBLE) {
            synchronized (this) {
                if (state == CaptureState.NOT_ELIGIBLE) {
                    state = CaptureState.ELIGIBLE;
                }
            }
        }
    }

    @Override
    public boolean isEligibleForCapturing() {
        return state != CaptureState.NOT_ELIGIBLE;
    }

    @Override
    public boolean havePreconditionsBeenChecked() {
        return state == CaptureState.PRECONDITIONS_PASSED
               || state == CaptureState.PRECONDITIONS_FAILED
               || state == CaptureState.STARTED;
    }

    @Override
    public void markPreconditionsFailed() {
        synchronized (this) {
            if (state == CaptureState.ELIGIBLE) {
                state = CaptureState.PRECONDITIONS_FAILED;
            }
        }
    }

    @Override
    public void markPreconditionsPassed(@Nullable String requestCharset, int numBytesToCapture) {
        if (numBytesToCapture > WebConfiguration.MAX_BODY_CAPTURE_BYTES) {
            throw new IllegalArgumentException("Capturing " + numBytesToCapture + " bytes is not supported, maximum is " + WebConfiguration.MAX_BODY_CAPTURE_BYTES + " bytes");
        }
        synchronized (this) {
            if (state == CaptureState.ELIGIBLE) {
                if (requestCharset != null) {
                    this.charset.append(requestCharset);
                }
                this.numBytesToCapture = numBytesToCapture;
                state = CaptureState.PRECONDITIONS_PASSED;
            }
        }
    }

    @Override
    public boolean startCapture() {
        if (state == CaptureState.PRECONDITIONS_PASSED) {
            synchronized (this) {
                if (state == CaptureState.PRECONDITIONS_PASSED) {
                    state = CaptureState.STARTED;
                    return true;
                }
            }
        }
        return false;
    }

    private void acquireBodyBufferIfRequired() {
        if (state != CaptureState.STARTED) {
            throw new IllegalStateException("Capturing has not been started!");
        }
        if (bodyBuffer == null) {
            bodyBuffer = BYTE_BUFFER_POOL.createInstance();
        }
    }

    @Override
    public void append(byte b) {
        acquireBodyBufferIfRequired();
        if (!isFull()) {
            bodyBuffer.put(b);
        }
    }

    @Override
    public void append(byte[] b, int offset, int len) {
        acquireBodyBufferIfRequired();
        int remaining = numBytesToCapture - bodyBuffer.position();
        if (remaining > 0) {
            bodyBuffer.put(b, offset, Math.min(len, remaining));
        }
    }

    @Override
    public boolean isFull() {
        if (bodyBuffer == null) {
            return false;
        }
        return bodyBuffer.position() >= numBytesToCapture;
    }

    @Nullable
    public CharSequence getCharset() {
        if (charset.length() == 0) {
            return null;
        }
        return charset;
    }

    @Nullable
    public ByteBuffer getBody() {
        return bodyBuffer;
    }
}
