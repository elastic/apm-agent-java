/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
 * %%
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
 * #L%
 */
package co.elastic.apm.agent.impl.transaction;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.sampling.Sampler;
import co.elastic.apm.agent.util.HexUtils;
import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * This is an implementation of the
 * <a href="https://w3c.github.io/trace-context/#traceparent-field">w3c traceparent header draft</a>.
 * <p>
 * As this is just a draft at the moment,
 * we don't use the official header name but prepend the custom prefix {@code Elastic-Apm-}.
 * </p>
 *
 * <pre>
 * elastic-apm-traceparent: 00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01
 * (_____________________)  () (______________________________) (______________) ()
 *            v             v                 v                        v         v
 *       Header name     Version           Trace-Id                Span-Id     Flags
 * </pre>
 */
public class TraceContext extends TraceContextHolder {

    public static final String TRACE_PARENT_HEADER = "elastic-apm-traceparent";
    public static final int SERIALIZED_LENGTH = 42;
    private static final int EXPECTED_LENGTH = 55;
    private static final int TRACE_ID_OFFSET = 3;
    private static final int PARENT_ID_OFFSET = 36;
    private static final int FLAGS_OFFSET = 53;
    private static final Logger logger = LoggerFactory.getLogger(TraceContext.class);
    /**
     * Helps to reduce allocations by caching {@link WeakReference}s to {@link ClassLoader}s
     */
    private static final WeakConcurrentMap<ClassLoader, WeakReference<ClassLoader>> classLoaderWeakReferenceCache = new WeakConcurrentMap.WithInlinedExpunction<>();
    private static final ChildContextCreator<TraceContextHolder<?>> FROM_PARENT = new ChildContextCreator<TraceContextHolder<?>>() {
        @Override
        public boolean asChildOf(TraceContext child, TraceContextHolder<?> parent) {
            child.asChildOf(parent.getTraceContext());
            return true;
        }
    };
    private static final ChildContextCreator<String> FROM_TRACEPARENT_HEADER = new ChildContextCreator<String>() {
        @Override
        public boolean asChildOf(TraceContext child, String traceparent) {
            if (traceparent != null) {
                return child.asChildOf(traceparent);
            }
            return false;
        }
    };

    private static final ChildContextCreator<ElasticApmTracer> FROM_ACTIVE = new ChildContextCreator<ElasticApmTracer>() {
        @Override
        public boolean asChildOf(TraceContext child, ElasticApmTracer tracer) {
            final TraceContextHolder active = tracer.getActive();
            if (active != null) {
                return fromParent().asChildOf(child, active.getTraceContext());

            }
            return false;
        }
    };
    private static final ChildContextCreator<Object> AS_ROOT = new ChildContextCreator<Object>() {
        @Override
        public boolean asChildOf(TraceContext child, Object ignore) {
            return false;
        }
    };
    private static final ChildContextCreatorTwoArg<byte[], String> FROM_SERIALIZED = new ChildContextCreatorTwoArg<byte[], String>() {
        @Override
        public boolean asChildOf(TraceContext child, byte[] serializedContext, @Nullable String serviceName) {
            child.asChildOf(serializedContext, serviceName);
            return true;
        }
    };

    private static final int TRACE_PARENT_LENGTH = EXPECTED_LENGTH;
    // ???????1 -> maybe recorded
    // ???????0 -> not recorded
    private static final byte FLAG_RECORDED = 0b0000_0001;
    private final Id traceId = Id.new128BitId();
    private final Id id;
    private final Id parentId = Id.new64BitId();
    private final Id transactionId = Id.new64BitId();
    private final StringBuilder outgoingHeader = new StringBuilder(TRACE_PARENT_LENGTH);
    private byte flags;
    private boolean discard;
    // weakly referencing to avoid CL leaks in case of leaked spans
    @Nullable
    private WeakReference<ClassLoader> applicationClassLoader;

    /**
     * Avoids clock drifts within a transaction.
     *
     * @see EpochTickClock
     */
    private EpochTickClock clock = new EpochTickClock();
    @Nullable
    private String serviceName;

    private TraceContext(ElasticApmTracer tracer, Id id) {
        super(tracer);
        this.id = id;
    }

    /**
     * Creates a new {@code traceparent}-compliant {@link TraceContext} with a 64 bit {@link #id}.
     * <p>
     * Note: the {@link #traceId} will still be 128 bit
     * </p>
     * @param tracer
     */
    public static TraceContext with64BitId(ElasticApmTracer tracer) {
        return new TraceContext(tracer, Id.new64BitId());
    }

    /**
     * Creates a new {@link TraceContext} with a 128 bit {@link #id},
     * suitable for errors,
     * as those might not have a trace reference and therefore require a larger id in order to be globally unique.
     * @param tracer
     */
    public static TraceContext with128BitId(ElasticApmTracer tracer) {
        return new TraceContext(tracer, Id.new128BitId());
    }

    public static ChildContextCreator<String> fromTraceparentHeader() {
        return FROM_TRACEPARENT_HEADER;
    }

    public static ChildContextCreator<ElasticApmTracer> fromActive() {
        return FROM_ACTIVE;
    }

    public static ChildContextCreator<TraceContextHolder<?>> fromParent() {
        return FROM_PARENT;
    }

    public static ChildContextCreator<?> asRoot() {
        return AS_ROOT;
    }

    public static ChildContextCreatorTwoArg<byte[], String> fromSerialized() {
        return FROM_SERIALIZED;
    }

    public boolean asChildOf(String traceParentHeader) {
        traceParentHeader = traceParentHeader.trim();
        try {
            if (traceParentHeader.length() < EXPECTED_LENGTH) {
                logger.warn("The traceparent header has to be at least 55 chars long, but was '{}'", traceParentHeader);
                return false;
            }
            if (!hasDashAtPosition(traceParentHeader, TRACE_ID_OFFSET - 1)
                || !hasDashAtPosition(traceParentHeader, PARENT_ID_OFFSET - 1)
                || !hasDashAtPosition(traceParentHeader, FLAGS_OFFSET - 1)) {
                logger.warn("The traceparent header has an invalid format: '{}'", traceParentHeader);
                return false;
            }
            if (traceParentHeader.length() > EXPECTED_LENGTH
                && !hasDashAtPosition(traceParentHeader, EXPECTED_LENGTH)) {
                logger.warn("The traceparent header has an invalid format: '{}'", traceParentHeader);
                return false;
            }
            if (traceParentHeader.startsWith("ff")) {
                logger.warn("Version ff is not supported");
                return false;
            }
            byte version = HexUtils.getNextByte(traceParentHeader, 0);
            if (version == 0 && traceParentHeader.length() > EXPECTED_LENGTH) {
                logger.warn("The traceparent header has to be exactly 55 chars long for version 00, but was '{}'", traceParentHeader);
                return false;
            }
            traceId.fromHexString(traceParentHeader, TRACE_ID_OFFSET);
            if (traceId.isEmpty()) {
                return false;
            }
            parentId.fromHexString(traceParentHeader, PARENT_ID_OFFSET);
            if (parentId.isEmpty()) {
                return false;
            }
            id.setToRandomValue();
            transactionId.copyFrom(id);
            // TODO don't blindly trust the flags from the caller
            // consider implement rate limiting and/or having a list of trusted sources
            // trace the request if it's either requested or if the parent has recorded it
            flags = getTraceOptions(traceParentHeader);
            clock.init();
            return true;
        } catch (IllegalArgumentException e) {
            logger.warn(e.getMessage());
            return false;
        } finally {
            onMutation();
        }
    }

    private boolean hasDashAtPosition(String traceParentHeader, int index) {
        return traceParentHeader.charAt(index) == '-';
    }

    public void asRootSpan(Sampler sampler) {
        traceId.setToRandomValue();
        id.setToRandomValue();
        transactionId.copyFrom(id);
        if (sampler.isSampled(traceId)) {
            this.flags = FLAG_RECORDED;
        }
        clock.init();
        onMutation();
    }

    public void asChildOf(TraceContext parent) {
        traceId.copyFrom(parent.traceId);
        parentId.copyFrom(parent.id);
        transactionId.copyFrom(parent.transactionId);
        flags = parent.flags;
        id.setToRandomValue();
        clock.init(parent.clock);
        serviceName = parent.serviceName;
        applicationClassLoader = parent.applicationClassLoader;
        onMutation();
    }

    private byte getTraceOptions(String traceParent) {
        return HexUtils.getNextByte(traceParent, FLAGS_OFFSET);
    }

    @Override
    public void resetState() {
        super.resetState();
        traceId.resetState();
        id.resetState();
        parentId.resetState();
        transactionId.resetState();
        outgoingHeader.setLength(0);
        flags = 0;
        discard = false;
        clock.resetState();
        serviceName = null;
        applicationClassLoader = null;
    }

    /**
     * The ID of the whole trace forest
     *
     * @return the trace id
     */
    public Id getTraceId() {
        return traceId;
    }

    public Id getId() {
        return id;
    }

    /**
     * The ID of the caller span (parent)
     *
     * @return the parent id
     */
    public Id getParentId() {
        return parentId;
    }

    public Id getTransactionId() {
        return transactionId;
    }

    public EpochTickClock getClock() {
        return clock;
    }

    /**
     * An alias for {@link #isRecorded()}
     *
     * @return {@code true} when this span should be sampled, {@code false} otherwise
     */
    public boolean isSampled() {
        return isRecorded();
    }

    /**
     * When {@code true}, this span should be recorded aka. sampled.
     *
     * @return {@code true} when this span should be recorded, {@code false} otherwise
     */
    boolean isRecorded() {
        return (flags & FLAG_RECORDED) == FLAG_RECORDED;
    }

    void setRecorded(boolean recorded) {
        if (recorded) {
            flags |= FLAG_RECORDED;
        } else {
            flags &= ~FLAG_RECORDED;
        }
    }

    public void setDiscard(boolean discard) {
        this.discard = discard;
    }

    public boolean isDiscard() {
        return discard;
    }

    /**
     * Returns the value of the {@code traceparent} header, as it was received.
     */
    public String getIncomingTraceParentHeader() {
        final StringBuilder sb = new StringBuilder(TRACE_PARENT_LENGTH);
        fillTraceParentHeader(sb, parentId);
        return sb.toString();
    }

    /**
     * Returns the value of the {@code traceparent} header for downstream services.
     */
    public StringBuilder getOutgoingTraceParentHeader() {
        if (outgoingHeader.length() == 0) {
            // for unsampled traces, propagate the ID of the transaction in calls to downstream services
            // such that the parentID of those transactions point to a transaction that exists
            // remember that we do report unsampled transactions
            fillTraceParentHeader(outgoingHeader, isSampled() ? id : transactionId);
        }
        return outgoingHeader;
    }

    private void fillTraceParentHeader(StringBuilder sb, Id spanId) {
        sb.append("00-");
        traceId.writeAsHex(sb);
        sb.append('-');
        spanId.writeAsHex(sb);
        sb.append('-');
        HexUtils.writeByteAsHex(flags, sb);
    }

    @Override
    public boolean isChildOf(TraceContextHolder parent) {
        return parent.getTraceContext().getTraceId().equals(traceId) && parent.getTraceContext().getId().equals(parentId);
    }

    public boolean hasContent() {
        return !id.isEmpty();
    }

    public void copyFrom(TraceContext other) {
        traceId.copyFrom(other.traceId);
        id.copyFrom(other.id);
        parentId.copyFrom(other.parentId);
        transactionId.copyFrom(other.transactionId);
        outgoingHeader.append(other.outgoingHeader);
        flags = other.flags;
        discard = other.discard;
        clock.init(other.clock);
        serviceName = other.serviceName;
        applicationClassLoader = other.applicationClassLoader;
        onMutation();
    }

    @Override
    public String toString() {
        return getOutgoingTraceParentHeader().toString();
    }

    private void onMutation() {
        outgoingHeader.setLength(0);
    }

    public boolean isRoot() {
        return parentId.isEmpty();
    }

    @Nullable
    public String getServiceName() {
        return serviceName;
    }

    /**
     * Overrides the {@link co.elastic.apm.agent.impl.payload.Service#name} property sent via the meta data Intake V2 event.
     *
     * @param serviceName the service name for this event
     */
    public void setServiceName(@Nullable String serviceName) {
        this.serviceName = serviceName;
    }

    @Override
    public TraceContext getTraceContext() {
        return this;
    }

    @Override
    public Span createSpan() {
        return tracer.startSpan(fromParent(), this);
    }

    @Override
    public Span createSpan(long epochMicros) {
        return tracer.startSpan(fromParent(), this, epochMicros);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TraceContext that = (TraceContext) o;
        return id.equals(that.id) &&
            traceId.equals(that.traceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(traceId, id, parentId, flags);
    }

    void setApplicationClassLoader(@Nullable ClassLoader classLoader) {
        if (classLoader != null) {
            WeakReference<ClassLoader> local = classLoaderWeakReferenceCache.get(classLoader);
            if (local == null) {
                local = new WeakReference<>(classLoader);
                classLoaderWeakReferenceCache.putIfAbsent(classLoader, local);
            }
            applicationClassLoader = local;
        }
    }

    @Nullable
    public ClassLoader getApplicationClassLoader() {
        if (applicationClassLoader != null) {
            return applicationClassLoader.get();
        } else {
            return null;
        }
    }

    public byte[] serialize() {
        byte[] result = new byte[SERIALIZED_LENGTH];
        serialize(result);
        return result;
    }

    public void serialize(byte[] buffer) {
        int offset = 0;
        offset = traceId.toBytes(buffer, offset);
        offset = transactionId.toBytes(buffer, offset);
        offset = id.toBytes(buffer, offset);
        buffer[offset++] = flags;
        buffer[offset++] = (byte) (discard ? 1 : 0);
        putLong(buffer, offset, clock.getOffset());
    }

    private void asChildOf(byte[] buffer, @Nullable String serviceName) {
        int offset = 0;
        offset = traceId.fromBytes(buffer, offset);
        offset = transactionId.fromBytes(buffer, offset);
        offset = parentId.fromBytes(buffer, offset);
        id.setToRandomValue();
        flags = buffer[offset++];
        discard = buffer[offset++] == (byte) 1;
        clock.init(getLong(buffer, offset));
        this.serviceName = serviceName;
        onMutation();
    }

    public void deserialize(byte[] buffer, @Nullable String serviceName) {
        int offset = 0;
        offset = traceId.fromBytes(buffer, offset);
        offset = transactionId.fromBytes(buffer, offset);
        offset = id.fromBytes(buffer, offset);
        flags = buffer[offset++];
        discard = buffer[offset++] == (byte) 1;
        clock.init(getLong(buffer, offset));
        this.serviceName = serviceName;
        onMutation();
    }

    private static void putLong(byte[] buffer, int offset, long l) {
        buffer[offset++] = (byte) l;
        buffer[offset++] = (byte) (l >> 8);
        buffer[offset++] = (byte) (l >> 16);
        buffer[offset++] = (byte) (l >> 24);
        buffer[offset++] = (byte) (l >> 32);
        buffer[offset++] = (byte) (l >> 40);
        buffer[offset++] = (byte) (l >> 48);
        buffer[offset] = (byte) (l >> 56);
    }

    private long getLong(byte[] buffer, int offset) {
        return ((long) buffer[offset + 7] << 56)
            | ((long) buffer[offset + 6] & 0xff) << 48
            | ((long) buffer[offset + 5] & 0xff) << 40
            | ((long) buffer[offset + 4] & 0xff) << 32
            | ((long) buffer[offset + 3] & 0xff) << 24
            | ((long) buffer[offset + 2] & 0xff) << 16
            | ((long) buffer[offset + 1] & 0xff) << 8
            | ((long) buffer[offset] & 0xff);
    }

    public interface ChildContextCreator<T> {
        boolean asChildOf(TraceContext child, T parent);
    }

    public interface ChildContextCreatorTwoArg<A, B> {
        boolean asChildOf(TraceContext child, A arg0, @Nullable B arg1);
    }

    public TraceContext copy() {
        final TraceContext copy;
        final int idLength = id.getLength();
        if (idLength == 8) {
            copy = TraceContext.with64BitId(tracer);
        } else if (idLength == 16) {
            copy = TraceContext.with128BitId(tracer);
        } else {
            throw new IllegalStateException("Id has invalid length: " + idLength);
        }
        copy.copyFrom(this);
        return copy;
    }

    /**
     * Wraps the provided {@link Runnable} and makes this {@link TraceContext} active in the {@link Runnable#run()} method.
     *
     * <p>
     * Note: does not activate the {@link AbstractSpan} but only the {@link TraceContext}.
     * This is useful if this span is closed in a different thread than the provided {@link Runnable} is executed in.
     * </p>
     */
    @Override
    public Runnable withActive(Runnable runnable) {
        return tracer.wrapRunnable(runnable, this);
    }

    /**
     * Wraps the provided {@link Callable} and makes this {@link TraceContext} active in the {@link Callable#call()} method.
     *
     * <p>
     * Note: does not activate the {@link AbstractSpan} but only the {@link TraceContext}.
     * This is useful if this span is closed in a different thread than the provided {@link java.util.concurrent.Callable} is executed in.
     * </p>
     */
    @Override
    public Callable withActive(Callable callable) {
        return tracer.wrapCallable(callable, this);
    }
}
