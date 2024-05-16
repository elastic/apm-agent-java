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
package co.elastic.apm.agent.impl.transaction;

import co.elastic.apm.agent.configuration.CoreConfigurationImpl;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.baggage.BaggageImpl;
import co.elastic.apm.agent.impl.sampling.Sampler;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import co.elastic.apm.agent.tracer.TraceContext;
import co.elastic.apm.agent.tracer.dispatch.HeaderGetter;
import co.elastic.apm.agent.tracer.dispatch.HeaderRemover;
import co.elastic.apm.agent.tracer.dispatch.HeaderSetter;
import co.elastic.apm.agent.tracer.dispatch.TextHeaderGetter;
import co.elastic.apm.agent.tracer.dispatch.TextHeaderSetter;
import co.elastic.apm.agent.tracer.dispatch.UTF8ByteHeaderGetter;
import co.elastic.apm.agent.tracer.dispatch.UTF8ByteHeaderSetter;
import co.elastic.apm.agent.tracer.pooling.Recyclable;
import co.elastic.apm.agent.tracer.util.HexUtils;
import co.elastic.apm.agent.util.ByteUtils;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * This is an implementation of the
 * <a href="https://w3c.github.io/trace-context/#traceparent-field">w3c traceparent header draft</a>.
 * <p>
 * As this is just a draft at the moment,
 * we don't use the official header name but prepend the custom prefix {@code Elastic-Apm-}.
 * </p>
 * <p>
 * Textual representation (e.g. HTTP header):
 * <pre>
 * elastic-apm-traceparent: 00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01
 * (_____________________)  () (______________________________) (______________) ()
 *            v             v                 v                        v         v
 *       Header name     Version           Trace-Id                Span-Id     Flags
 * </pre>
 * <p>
 */
public class TraceContextImpl implements Recyclable, TraceContext {

    public static final String ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME = "elastic-apm-traceparent";
    public static final String W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME = "traceparent";
    public static final String TRACESTATE_HEADER_NAME = "tracestate";
    public static final int SERIALIZED_LENGTH = 51;
    private static final int TEXT_HEADER_EXPECTED_LENGTH = 55;
    private static final int TEXT_HEADER_TRACE_ID_OFFSET = 3;
    private static final int TEXT_HEADER_PARENT_ID_OFFSET = 36;
    private static final int TEXT_HEADER_FLAGS_OFFSET = 53;
    private static final Logger logger = LoggerFactory.getLogger(TraceContextImpl.class);

    private static final Double SAMPLE_RATE_ZERO = 0d;

    public static final Set<String> TRACE_TEXTUAL_HEADERS;

    static {
        Set<String> traceParentTextualHeaders = new HashSet<>();
        traceParentTextualHeaders.add(ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME);
        traceParentTextualHeaders.add(W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME);
        traceParentTextualHeaders.add(TRACESTATE_HEADER_NAME);
        TRACE_TEXTUAL_HEADERS = Collections.unmodifiableSet(traceParentTextualHeaders);
    }

    private static final ChildContextCreator<TraceContextImpl> FROM_PARENT_CONTEXT = new ChildContextCreator<TraceContextImpl>() {
        @Override
        public boolean asChildOf(TraceContextImpl child, TraceContextImpl parent) {
            child.asChildOf(parent);
            return true;
        }
    };
    private static final ChildContextCreator<AbstractSpanImpl<?>> FROM_PARENT = new ChildContextCreator<AbstractSpanImpl<?>>() {
        @Override
        public boolean asChildOf(TraceContextImpl child, AbstractSpanImpl<?> parent) {
            child.asChildOf(parent.getTraceContext());
            return true;
        }
    };

    private static final HeaderGetter.HeaderConsumer<String, TraceContextImpl> STRING_TRACESTATE_HEADER_CONSUMER = new HeaderGetter.HeaderConsumer<String, TraceContextImpl>() {
        @Override
        public void accept(@Nullable String tracestateHeaderValue, TraceContextImpl state) {
            state.addTraceStateHeader(tracestateHeaderValue, CharAccessor.forCharSequence());
        }
    };

    private static final HeaderGetter.HeaderConsumer<byte[], TraceContextImpl> UTF8_BYTES_TRACESTATE_HEADER_CONSUMER = new HeaderGetter.HeaderConsumer<byte[], TraceContextImpl>() {
        @Override
        public void accept(@Nullable byte[] tracestateHeaderValue, TraceContextImpl state) {
            state.addTraceStateHeader(tracestateHeaderValue, CharAccessor.forAsciiBytes());
        }
    };

    public <T, C> boolean asChildOf(@Nullable C carrier, HeaderGetter<T, C> headerGetter) {
        return asChildOf(carrier, headerGetter, true);
    }

    @SuppressWarnings("unchecked")
    public <T, C> boolean asChildOf(@Nullable C carrier, HeaderGetter<T, C> headerGetter, boolean parseTraceState) {
        CharAccessor<T> headerValueAccessor;
        HeaderGetter.HeaderConsumer<T, TraceContextImpl> traceStateConsumer;
        if (headerGetter instanceof TextHeaderGetter) {
            headerValueAccessor = (CharAccessor<T>) CharAccessor.forCharSequence();
            traceStateConsumer = (HeaderGetter.HeaderConsumer<T, TraceContextImpl>) STRING_TRACESTATE_HEADER_CONSUMER;
        } else if (headerGetter instanceof UTF8ByteHeaderGetter) {
            headerValueAccessor = (CharAccessor<T>) CharAccessor.forAsciiBytes();
            traceStateConsumer = (HeaderGetter.HeaderConsumer<T, TraceContextImpl>) UTF8_BYTES_TRACESTATE_HEADER_CONSUMER;
        } else {
            throw new IllegalArgumentException("HeaderGetter must be either a TextHeaderGetter or UTF8ByteHeaderGetter: " + headerGetter.getClass().getName());
        }
        boolean valid = extractTraceParentFromHeaders(carrier, headerGetter, headerValueAccessor);
        if (valid && parseTraceState) {
            headerGetter.forEach(TRACESTATE_HEADER_NAME, carrier, this, traceStateConsumer);
        }
        return valid;
    }

    private <T, C> boolean extractTraceParentFromHeaders(@Nullable C carrier, HeaderGetter<T, C> traceContextHeaderGetter, CharAccessor<? super T> charAccessor) {
        if (carrier == null) {
            return false;
        }

        boolean isValid = false;
        T traceparent = traceContextHeaderGetter.getFirstHeader(W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME, carrier);
        if (traceparent != null) {
            isValid = asChildOf(traceparent, charAccessor);
        }

        if (!isValid) {
            // Look for the legacy Elastic traceparent header (in case this comes from an older agent)
            traceparent = traceContextHeaderGetter.getFirstHeader(ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME, carrier);
            if (traceparent != null) {
                isValid = asChildOf(traceparent, charAccessor);
            }
        }

        return isValid;
    }

    public static <C> boolean containsTraceContextTextHeaders(C carrier, TextHeaderGetter<C> headerGetter) {
        // We assume that this header is always present if we found any of the other headers.
        return headerGetter.getFirstHeader(W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME, carrier) != null;
    }

    public static <C> void removeTraceContextHeaders(C carrier, HeaderRemover<C> headerRemover) {
        headerRemover.remove(W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME, carrier);
        headerRemover.remove(ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME, carrier);
        headerRemover.remove(TRACESTATE_HEADER_NAME, carrier);
    }

    public static <S, D> void copyTraceContextTextHeaders(S source, TextHeaderGetter<S> headerGetter, D destination, TextHeaderSetter<D> headerSetter) {
        String w3cApmTraceParent = headerGetter.getFirstHeader(W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME, source);
        if (w3cApmTraceParent != null) {
            headerSetter.setHeader(W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME, w3cApmTraceParent, destination);
        }
        String elasticApmTraceParent = headerGetter.getFirstHeader(ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME, source);
        if (elasticApmTraceParent != null) {
            headerSetter.setHeader(ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME, elasticApmTraceParent, destination);
        }
        // copying only the first tracestate header
        String tracestate = headerGetter.getFirstHeader(TRACESTATE_HEADER_NAME, source);
        if (tracestate != null) {
            headerSetter.setHeader(TRACESTATE_HEADER_NAME, tracestate, destination);
        }
    }

    // ???????1 -> maybe recorded
    // ???????0 -> not recorded
    private static final byte FLAG_RECORDED = 0b0000_0001;
    private final IdImpl traceId = IdImpl.new128BitId();
    private final ElasticApmTracer tracer;
    private final IdImpl id;
    private final IdImpl parentId = IdImpl.new64BitId();
    private final IdImpl transactionId = IdImpl.new64BitId();
    private final StringBuilder outgoingTextHeader = new StringBuilder(TEXT_HEADER_EXPECTED_LENGTH);
    private byte flags;
    private boolean discardable = true;

    private final TraceState traceState;

    final CoreConfigurationImpl coreConfiguration;

    /**
     * Avoids clock drifts within a transaction.
     *
     * @see EpochTickClock
     */
    private final EpochTickClock clock = new EpochTickClock();

    @Nullable
    private String serviceName;

    @Nullable
    private String serviceVersion;

    private TraceContextImpl(ElasticApmTracer tracer, IdImpl id) {
        coreConfiguration = tracer.getConfig(CoreConfigurationImpl.class);
        traceState = new TraceState();
        traceState.setSizeLimit(coreConfiguration.getTracestateSizeLimit());
        this.tracer = tracer;
        this.id = id;
    }

    /**
     * Creates a new {@code traceparent}-compliant {@link TraceContextImpl} with a 64 bit {@link #id}.
     * <p>
     * Note: the {@link #traceId} will still be 128 bit
     * </p>
     *
     * @param tracer a valid tracer
     */
    public static TraceContextImpl with64BitId(ElasticApmTracer tracer) {
        return new TraceContextImpl(tracer, IdImpl.new64BitId());
    }

    /**
     * Creates a new {@link TraceContextImpl} with a 128 bit {@link #id},
     * suitable for errors,
     * as those might not have a trace reference and therefore require a larger id in order to be globally unique.
     *
     * @param tracer a valid tracer
     */
    public static TraceContextImpl with128BitId(ElasticApmTracer tracer) {
        return new TraceContextImpl(tracer, IdImpl.new128BitId());
    }

    public static ChildContextCreator<TraceContextImpl> fromParentContext() {
        return FROM_PARENT_CONTEXT;
    }

    public static ChildContextCreator<AbstractSpanImpl<?>> fromParent() {
        return FROM_PARENT;
    }

    boolean asChildOf(String traceParentHeader) {
        return asChildOf(traceParentHeader, CharAccessor.forCharSequence());
    }

    /**
     * Tries to set trace context identifiers (Id, parent, ...) from traceparent text header value
     *
     * @param traceParentHeader traceparent text header value
     * @return {@literal true} if header value is valid, {@literal false} otherwise
     */
    <T> boolean asChildOf(T traceParentHeader, CharAccessor<T> charAccessor) {

        int leadingWs = charAccessor.getLeadingWhitespaceCount(traceParentHeader);
        int trailingWs = charAccessor.getTrailingWhitespaceCount(traceParentHeader);

        try {
            int trimmedLen = Math.max(0, charAccessor.length(traceParentHeader) - leadingWs - trailingWs);
            if (trimmedLen < TEXT_HEADER_EXPECTED_LENGTH) {
                logger.warn("The traceparent header has to be at least 55 chars long, but was '{}'", trimmedLen);
                return false;
            }
            if (noDashAtPosition(traceParentHeader, leadingWs + TEXT_HEADER_TRACE_ID_OFFSET - 1, charAccessor)
                || noDashAtPosition(traceParentHeader, leadingWs + TEXT_HEADER_PARENT_ID_OFFSET - 1, charAccessor)
                || noDashAtPosition(traceParentHeader, leadingWs + TEXT_HEADER_FLAGS_OFFSET - 1, charAccessor)) {
                if (logger.isWarnEnabled()) {
                    logger.warn("The traceparent header has an invalid format: '{}'", charAccessor.asString(traceParentHeader));
                }
                return false;
            }
            if (trimmedLen > TEXT_HEADER_EXPECTED_LENGTH
                && noDashAtPosition(traceParentHeader, leadingWs + TEXT_HEADER_EXPECTED_LENGTH, charAccessor)) {
                if (logger.isWarnEnabled()) {
                    logger.warn("The traceparent header has an invalid format: '{}'", charAccessor.asString(traceParentHeader));
                }
                return false;
            }
            if (charAccessor.containsAtOffset(traceParentHeader, leadingWs, "ff")) {
                if (logger.isWarnEnabled()) {
                    logger.warn("Version ff is not supported");
                }
                return false;
            }
            byte version = charAccessor.readHexByte(traceParentHeader, leadingWs);
            if (version == 0 && trimmedLen > TEXT_HEADER_EXPECTED_LENGTH) {
                if (logger.isWarnEnabled()) {
                    logger.warn("The traceparent header has to be exactly 55 chars long for version 00, but was '{}'", charAccessor.asString(traceParentHeader));
                }
                return false;
            }
            traceId.fromHexString(traceParentHeader, leadingWs + TEXT_HEADER_TRACE_ID_OFFSET, charAccessor);
            if (traceId.isEmpty()) {
                return false;
            }
            parentId.fromHexString(traceParentHeader, leadingWs + TEXT_HEADER_PARENT_ID_OFFSET, charAccessor);
            if (parentId.isEmpty()) {
                return false;
            }
            id.setToRandomValue();
            transactionId.copyFrom(id);
            // TODO don't blindly trust the flags from the caller
            // consider implement rate limiting and/or having a list of trusted sources
            // trace the request if it's either requested or if the parent has recorded it
            flags = charAccessor.readHexByte(traceParentHeader, TEXT_HEADER_FLAGS_OFFSET + leadingWs);
            clock.init();
            return true;
        } catch (IllegalArgumentException e) {
            logger.warn(e.getMessage());
            return false;
        } finally {
            onMutation();
        }
    }

    private <T> boolean noDashAtPosition(T traceParentHeader, int index, CharAccessor<T> accessor) {
        return accessor.charAt(traceParentHeader, index) != '-';
    }

    private <T> void addTraceStateHeader(@Nullable T tracestateHeaderValue, CharAccessor<T> charAccessor) {
        if (tracestateHeaderValue != null) {
            traceState.addTextHeader(charAccessor.asString(tracestateHeaderValue));
        }
    }

    public void asRootSpan(Sampler sampler) {
        traceId.setToRandomValue();
        id.setToRandomValue();
        transactionId.copyFrom(id);
        if (sampler.isSampled(traceId)) {
            flags = FLAG_RECORDED;
            traceState.set(sampler.getSampleRate(), sampler.getTraceStateHeader());
        }
        clock.init();
        onMutation();
    }

    public void asChildOf(TraceContextImpl parent) {
        traceId.copyFrom(parent.traceId);
        parentId.copyFrom(parent.id);
        transactionId.copyFrom(parent.transactionId);
        flags = parent.flags;
        id.setToRandomValue();
        clock.init(parent.clock);
        serviceName = parent.serviceName;
        serviceVersion = parent.serviceVersion;
        traceState.copyFrom(parent.traceState);
        onMutation();
    }

    @Override
    public void resetState() {
        traceId.resetState();
        id.resetState();
        parentId.resetState();
        transactionId.resetState();
        outgoingTextHeader.setLength(0);
        flags = 0;
        discardable = true;
        clock.resetState();
        serviceName = null;
        serviceVersion = null;
        traceState.resetState();
        traceState.setSizeLimit(coreConfiguration.getTracestateSizeLimit());
    }

    @Override
    public IdImpl getTraceId() {
        return traceId;
    }

    @Override
    public IdImpl getId() {
        return id;
    }

    @Override
    public IdImpl getParentId() {
        return parentId;
    }

    @Override
    public IdImpl getTransactionId() {
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
     * Returns the sample rate used for this transaction/span between 0.0 and 1.0 or {@link Double#NaN} if sample rate is unknown
     *
     * @return sample rate
     */
    public double getSampleRate() {
        if (isRecorded()) {
            return traceState.getSampleRate();
        } else {
            return SAMPLE_RATE_ZERO;
        }
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

    void setNonDiscardable() {
        this.discardable = false;
    }

    boolean isDiscardable() {
        return discardable;
    }

    /**
     * FOR TESTING PURPOSES ONLY
     * Returns the value of the {@code traceparent} header, as it was received.
     */
    String getIncomingTraceParentHeader() {
        final StringBuilder sb = new StringBuilder(TEXT_HEADER_EXPECTED_LENGTH);
        fillTraceParentHeader(sb, parentId);
        return sb.toString();
    }

    /**
     * Sets Trace context text headers, using this context as parent, on the provided carrier using the provided setter
     *
     * @param carrier      the text headers carrier
     * @param headerSetter a setter implementing the actual addition of headers to the headers carrier
     * @param <C>          the header carrier type, for example - an HTTP request
     */
    <T, C> void propagateTraceContext(C carrier, HeaderSetter<T, C> headerSetter) {
        if (coreConfiguration.isOutgoingTraceContextHeadersInjectionDisabled()) {
            logger.debug("Outgoing TraceContext header injection is disabled");
            return;
        }

        T outgoingTraceParent = getOutgoingTraceParentTextHeader(headerSetter);

        headerSetter.setHeader(W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME, outgoingTraceParent, carrier);
        if (coreConfiguration.isElasticTraceparentHeaderEnabled()) {
            headerSetter.setHeader(ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME, outgoingTraceParent, carrier);
        }

        T outgoingTraceState = getOutgoingTraceStateHeader(headerSetter);
        if (outgoingTraceState != null) {
            headerSetter.setHeader(TRACESTATE_HEADER_NAME, outgoingTraceState, carrier);
        }
        logger.trace("Trace context headers added to {}", carrier);
    }

    <T> T getOutgoingTraceParentTextHeader(HeaderSetter<T, ?> headerSetter) {
        StringBuilder outgoingTraceParentTextHeader = getOutgoingTraceParentTextHeader();
        if (headerSetter instanceof TextHeaderSetter) {
            return (T) outgoingTraceParentTextHeader.toString();
        } else if (headerSetter instanceof UTF8ByteHeaderSetter) {
            int length = outgoingTraceParentTextHeader.length();
            byte[] result = new byte[length];
            for (int i = 0; i < length; i++) {
                char c = outgoingTraceParentTextHeader.charAt(i);
                if (c > 127) {
                    throw new IllegalStateException("Expected traceparent header to be ascii only");
                }
                result[i] = (byte) c;
            }
            return (T) result;
        } else {
            throw new IllegalArgumentException("HeaderSetter must be either a TextHeaderSetter or UTF8ByteHeaderSetter: " + headerSetter.getClass().getName());
        }
    }

    @Nullable
    <T> T getOutgoingTraceStateHeader(HeaderSetter<T, ?> headerSetter) {
        String outgoingTraceState = traceState.toTextHeader();
        if (outgoingTraceState == null) {
            return null;
        }
        if (headerSetter instanceof TextHeaderSetter) {
            return (T) outgoingTraceState;
        } else if (headerSetter instanceof UTF8ByteHeaderSetter) {
            return (T) outgoingTraceState.getBytes(StandardCharsets.UTF_8);
        } else {
            throw new IllegalArgumentException("HeaderSetter must be either a TextHeaderSetter or UTF8ByteHeaderSetter: " + headerSetter.getClass().getName());
        }
    }

    /**
     * @return the value of the {@code traceparent} header for downstream services.
     */
    StringBuilder getOutgoingTraceParentTextHeader() {
        if (outgoingTextHeader.length() == 0) {
            synchronized (outgoingTextHeader) {
                if (outgoingTextHeader.length() == 0) {
                    // for unsampled traces, propagate the ID of the transaction in calls to downstream services
                    // such that the parentID of those transactions point to a transaction that exists
                    // remember that we do report unsampled transactions
                    fillTraceParentHeader(outgoingTextHeader, isSampled() ? id : transactionId);
                }
            }
        }
        return outgoingTextHeader;
    }

    private void fillTraceParentHeader(StringBuilder sb, IdImpl spanId) {
        sb.append("00-");
        traceId.writeAsHex(sb);
        sb.append('-');
        spanId.writeAsHex(sb);
        sb.append('-');
        HexUtils.writeByteAsHex(flags, sb);
    }

    public boolean isChildOf(TraceContextImpl other) {
        return other.getTraceId().equals(traceId) && other.getId().equals(parentId);
    }

    public boolean hasContent() {
        return !id.isEmpty();
    }

    public void copyFrom(TraceContextImpl other) {
        traceId.copyFrom(other.traceId);
        id.copyFrom(other.id);
        parentId.copyFrom(other.parentId);
        transactionId.copyFrom(other.transactionId);
        flags = other.flags;
        discardable = other.discardable;
        clock.init(other.clock);
        serviceName = other.serviceName;
        serviceVersion = other.serviceVersion;
        traceState.copyFrom(other.traceState);
        onMutation();
    }

    @Override
    public String toString() {
        return getOutgoingTraceParentTextHeader().toString();
    }

    private void onMutation() {
        outgoingTextHeader.setLength(0);
    }

    public boolean isRoot() {
        return parentId.isEmpty();
    }

    @Nullable
    public String getServiceName() {
        return serviceName;
    }

    /**
     * Overrides the {@code co.elastic.apm.agent.impl.payload.Service#name} and {@code co.elastic.apm.agent.impl.payload.Service#version} properties sent via the meta data Intake V2 event.
     *
     * @param serviceName    the service name for this event
     * @param serviceVersion the service version for this event
     */
    public void setServiceInfo(@Nullable String serviceName, @Nullable String serviceVersion) {
        if (serviceName == null || serviceName.isEmpty()) {
            return;
        }
        this.serviceName = serviceName;
        this.serviceVersion = serviceVersion;
    }

    @Nullable
    public String getServiceVersion() {
        return serviceVersion;
    }


    /**
     * Creates a child span from this trace context. The span will not have any baggage assigned.
     *
     * @param epochMicros the span start time
     * @return the newly started span
     */
    public SpanImpl createSpan(long epochMicros) {
        return tracer.startSpan(fromParentContext(), this, BaggageImpl.EMPTY, epochMicros);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TraceContextImpl that = (TraceContextImpl) o;
        return id.equals(that.id) &&
               traceId.equals(that.traceId);
    }

    public boolean idEquals(@Nullable TraceContextImpl o) {
        if (this == o) return true;
        if (o == null) return false;
        return id.equals(o.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(traceId, id, parentId, flags);
    }

    public TraceState getTraceState() {
        return traceState;
    }

    public byte[] serialize() {
        byte[] result = new byte[SERIALIZED_LENGTH];
        serialize(result);
        return result;
    }

    public void serialize(byte[] buffer) {
        int offset = 0;
        offset = traceId.toBytes(buffer, offset);
        offset = id.toBytes(buffer, offset);
        offset = transactionId.toBytes(buffer, offset);
        buffer[offset++] = parentId.isEmpty() ? (byte) 0 : (byte) 1;
        offset = parentId.toBytes(buffer, offset);
        buffer[offset++] = flags;
        buffer[offset++] = (byte) (discardable ? 1 : 0);
        ByteUtils.putLong(buffer, offset, clock.getOffset());
    }

    public void deserialize(byte[] buffer, @Nullable String serviceName, @Nullable String serviceVersion) {
        int offset = 0;
        offset += traceId.fromBytes(buffer, offset);
        offset += id.fromBytes(buffer, offset);
        offset += transactionId.fromBytes(buffer, offset);
        if (buffer[offset++] != 0) {
            offset += parentId.fromBytes(buffer, offset);
        } else {
            parentId.resetState();
            offset += 8;
        }
        flags = buffer[offset++];
        discardable = buffer[offset++] == (byte) 1;
        clock.init(ByteUtils.getLong(buffer, offset));
        this.serviceName = serviceName;
        this.serviceVersion = serviceVersion;
        onMutation();
    }

    public static long getSpanId(byte[] serializedTraceContext) {
        return ByteUtils.getLong(serializedTraceContext, 16);
    }

    public static long getParentId(byte[] serializedTraceContext) {
        return ByteUtils.getLong(serializedTraceContext, 33);
    }

    public boolean traceIdAndIdEquals(byte[] serialized) {
        return id.dataEquals(serialized, traceId.getLength()) && traceId.dataEquals(serialized, 0);
    }

    public byte getFlags() {
        return flags;
    }

    public interface ChildContextCreator<T> {
        boolean asChildOf(TraceContextImpl child, T parent);
    }

    public TraceContextImpl copy() {
        final TraceContextImpl copy;
        final int idLength = id.getLength();
        if (idLength == 8) {
            copy = TraceContextImpl.with64BitId(tracer);
        } else if (idLength == 16) {
            copy = TraceContextImpl.with128BitId(tracer);
        } else {
            throw new IllegalStateException("Id has invalid length: " + idLength);
        }
        copy.copyFrom(this);
        return copy;
    }
}
