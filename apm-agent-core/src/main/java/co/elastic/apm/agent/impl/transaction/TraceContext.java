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

import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.Tracer;
import co.elastic.apm.agent.impl.sampling.Sampler;
import co.elastic.apm.agent.objectpool.Recyclable;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakConcurrent;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakMap;
import co.elastic.apm.agent.util.ByteUtils;
import co.elastic.apm.agent.util.ClassLoaderUtils;
import co.elastic.apm.agent.util.HexUtils;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

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
 * Binary representation (e.g. 0.11.0.0+ Kafka record header), based on
 * https://github.com/elastic/apm/blob/master/docs/agent-development.md#binary-fields:
 * <pre>
 *      traceparent     = version version_format
 *      version         = 1BYTE                   ; version is 0 in the current spec
 *      version_format  = "{ 0x0 }" trace-id "{ 0x1 }" parent-id "{ 0x2 }" trace-flags
 *      trace-id        = 16BYTES
 *      parent-id       = 8BYTES
 *      trace-flags     = 1BYTE  ; only the least significant bit is used
 * </pre>
 * For example:
 * <pre>
 * elasticapmparent:   [0,
 *                      0, 75, 249, 47, 53, 119, 179, 77, 166, 163, 206, 146, 157, 0, 14, 71, 54,
 *                      1, 52, 240, 103, 170, 11, 169, 2, 183,
 *                      2, 1]
 * </pre>
 */
public class TraceContext implements Recyclable {

    public static final String ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME = "elastic-apm-traceparent";
    public static final String W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME = "traceparent";
    public static final String TRACESTATE_HEADER_NAME = "tracestate";
    public static final int SERIALIZED_LENGTH = 42;
    private static final int TEXT_HEADER_EXPECTED_LENGTH = 55;
    private static final int TEXT_HEADER_TRACE_ID_OFFSET = 3;
    private static final int TEXT_HEADER_PARENT_ID_OFFSET = 36;
    private static final int TEXT_HEADER_FLAGS_OFFSET = 53;

    public static final String TRACE_PARENT_BINARY_HEADER_NAME = "elasticapmtraceparent";
    public static final int BINARY_FORMAT_EXPECTED_LENGTH = 29;
    private static final byte BINARY_FORMAT_CURRENT_VERSION = (byte) 0b0000_0000;
    // one byte for the trace-id field id (0x00), followed by 16 bytes of the actual ID
    private static final int BINARY_FORMAT_TRACE_ID_OFFSET = 1;
    private static final byte BINARY_FORMAT_TRACE_ID_FIELD_ID = (byte) 0b0000_0000;
    // one byte for the parent-id field id (0x01), followed by 8 bytes of the actual ID
    private static final int BINARY_FORMAT_PARENT_ID_OFFSET = 18;
    private static final byte BINARY_FORMAT_PARENT_ID_FIELD_ID = (byte) 0b0000_0001;
    // one byte for the flags field id (0x02), followed by two bytes of flags contents
    private static final int BINARY_FORMAT_FLAGS_OFFSET = 27;
    private static final byte BINARY_FORMAT_FLAGS_FIELD_ID = (byte) 0b0000_0010;
    private static final Logger logger = LoggerFactory.getLogger(TraceContext.class);

    private static final Double SAMPLE_RATE_ZERO = 0d;

    /**
     * Helps to reduce allocations by caching {@link WeakReference}s to {@link ClassLoader}s
     */
    private static final WeakMap<ClassLoader, WeakReference<ClassLoader>> classLoaderWeakReferenceCache = WeakConcurrent.buildMap();
    private static final ChildContextCreator<TraceContext> FROM_PARENT_CONTEXT = new ChildContextCreator<TraceContext>() {
        @Override
        public boolean asChildOf(TraceContext child, TraceContext parent) {
            child.asChildOf(parent);
            return true;
        }
    };
    private static final ChildContextCreator<AbstractSpan<?>> FROM_PARENT = new ChildContextCreator<AbstractSpan<?>>() {
        @Override
        public boolean asChildOf(TraceContext child, AbstractSpan<?> parent) {
            child.asChildOf(parent.getTraceContext());
            return true;
        }
    };
    private static final HeaderGetter.HeaderConsumer<String, TraceContext> TRACESTATE_HEADER_CONSUMER = new HeaderGetter.HeaderConsumer<String, TraceContext>() {
        @Override
        public void accept(@Nullable String tracestateHeaderValue, TraceContext state) {
            if (tracestateHeaderValue != null) {
                state.traceState.addTextHeader(tracestateHeaderValue);
            }
        }
    };
    private static final ChildContextCreatorTwoArg FROM_TRACE_CONTEXT_TEXT_HEADERS =
        new ChildContextCreatorTwoArg<Object, TextHeaderGetter<Object>>() {
            @Override
            public boolean asChildOf(TraceContext child, @Nullable Object carrier, TextHeaderGetter<Object> traceContextHeaderGetter) {
                if (carrier == null) {
                    return false;
                }

                boolean isValid = false;
                String traceparent = traceContextHeaderGetter.getFirstHeader(W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME, carrier);
                if (traceparent != null) {
                    isValid = child.asChildOf(traceparent);
                }

                if (!isValid) {
                    // Look for the legacy Elastic traceparent header (in case this comes from an older agent)
                    traceparent = traceContextHeaderGetter.getFirstHeader(ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME, carrier);
                    if (traceparent != null) {
                        isValid = child.asChildOf(traceparent);
                    }
                }

                if (isValid) {
                    // as per spec, the tracestate header can be multi-valued
                    traceContextHeaderGetter.forEach(TRACESTATE_HEADER_NAME, carrier, child, TRACESTATE_HEADER_CONSUMER);
                }

                return isValid;
            }
        };
    private static final ChildContextCreatorTwoArg FROM_TRACE_CONTEXT_BINARY_HEADERS =
        new ChildContextCreatorTwoArg<Object, BinaryHeaderGetter<Object>>() {
            @Override
            public boolean asChildOf(TraceContext child, @Nullable Object carrier, BinaryHeaderGetter<Object> traceContextHeaderGetter) {
                if (carrier == null) {
                    return false;
                }
                byte[] traceparent = traceContextHeaderGetter.getFirstHeader(TRACE_PARENT_BINARY_HEADER_NAME, carrier);
                if (traceparent != null) {
                    return child.asChildOf(traceparent);
                }
                return false;
            }
        };
    private static final ChildContextCreator<Tracer> FROM_ACTIVE = new ChildContextCreator<Tracer>() {
        @Override
        public boolean asChildOf(TraceContext child, Tracer tracer) {
            final AbstractSpan<?> active = tracer.getActive();
            if (active != null) {
                return fromParent().asChildOf(child, active);

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


    public static <C> boolean containsTraceContextTextHeaders(C carrier, TextHeaderGetter<C> headerGetter) {
        return headerGetter.getFirstHeader(W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME, carrier) != null;
    }

    public static <C> void removeTraceContextHeaders(C carrier, HeaderRemover<C> headerRemover) {
        headerRemover.remove(W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME, carrier);
        headerRemover.remove(ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME, carrier);
        headerRemover.remove(TRACESTATE_HEADER_NAME, carrier);
        headerRemover.remove(TRACE_PARENT_BINARY_HEADER_NAME, carrier);
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
    private final Id traceId = Id.new128BitId();
    private final ElasticApmTracer tracer;
    private final Id id;
    private final Id parentId = Id.new64BitId();
    private final Id transactionId = Id.new64BitId();
    private final StringBuilder outgoingTextHeader = new StringBuilder(TEXT_HEADER_EXPECTED_LENGTH);
    private byte flags;
    private boolean discardable = true;
    // weakly referencing to avoid CL leaks in case of leaked spans
    @Nullable
    private WeakReference<ClassLoader> applicationClassLoader;
    private final TraceState traceState;

    final CoreConfiguration coreConfiguration;

    /**
     * Avoids clock drifts within a transaction.
     *
     * @see EpochTickClock
     */
    private final EpochTickClock clock = new EpochTickClock();

    @Nullable
    private String serviceName;

    private TraceContext(ElasticApmTracer tracer, Id id) {
        coreConfiguration = tracer.getConfig(CoreConfiguration.class);
        traceState = new TraceState();
        traceState.setSizeLimit(coreConfiguration.getTracestateSizeLimit());
        this.tracer = tracer;
        this.id = id;
    }

    /**
     * Creates a new {@code traceparent}-compliant {@link TraceContext} with a 64 bit {@link #id}.
     * <p>
     * Note: the {@link #traceId} will still be 128 bit
     * </p>
     * @param tracer a valid tracer
     */
    public static TraceContext with64BitId(ElasticApmTracer tracer) {
        return new TraceContext(tracer, Id.new64BitId());
    }

    /**
     * Creates a new {@link TraceContext} with a 128 bit {@link #id},
     * suitable for errors,
     * as those might not have a trace reference and therefore require a larger id in order to be globally unique.
     *
     * @param tracer a valid tracer
     */
    public static TraceContext with128BitId(ElasticApmTracer tracer) {
        return new TraceContext(tracer, Id.new128BitId());
    }

    @SuppressWarnings("unchecked")
    public static <C> ChildContextCreatorTwoArg<C, TextHeaderGetter<C>> getFromTraceContextTextHeaders() {
        return (ChildContextCreatorTwoArg<C, TextHeaderGetter<C>>) FROM_TRACE_CONTEXT_TEXT_HEADERS;
    }

    @SuppressWarnings("unchecked")
    public static <C> ChildContextCreatorTwoArg<C, BinaryHeaderGetter<C>> getFromTraceContextBinaryHeaders() {
        return (ChildContextCreatorTwoArg<C, BinaryHeaderGetter<C>>) FROM_TRACE_CONTEXT_BINARY_HEADERS;
    }

    public static ChildContextCreator<Tracer> fromActive() {
        return FROM_ACTIVE;
    }

    public static ChildContextCreator<TraceContext> fromParentContext() {
        return FROM_PARENT_CONTEXT;
    }

    public static ChildContextCreator<AbstractSpan<?>> fromParent() {
        return FROM_PARENT;
    }

    public static ChildContextCreator<?> asRoot() {
        return AS_ROOT;
    }

    /**
     * Tries to set trace context identifiers (Id, parent, ...) from traceparent text header value
     *
     * @param traceParentHeader traceparent text header value
     * @return {@literal true} if header value is valid, {@literal false} otherwise
     */
    boolean asChildOf(String traceParentHeader) {
        traceParentHeader = traceParentHeader.trim();
        try {
            if (traceParentHeader.length() < TEXT_HEADER_EXPECTED_LENGTH) {
                logger.warn("The traceparent header has to be at least 55 chars long, but was '{}'", traceParentHeader);
                return false;
            }
            if (noDashAtPosition(traceParentHeader, TEXT_HEADER_TRACE_ID_OFFSET - 1)
                || noDashAtPosition(traceParentHeader, TEXT_HEADER_PARENT_ID_OFFSET - 1)
                || noDashAtPosition(traceParentHeader, TEXT_HEADER_FLAGS_OFFSET - 1)) {
                logger.warn("The traceparent header has an invalid format: '{}'", traceParentHeader);
                return false;
            }
            if (traceParentHeader.length() > TEXT_HEADER_EXPECTED_LENGTH
                && noDashAtPosition(traceParentHeader, TEXT_HEADER_EXPECTED_LENGTH)) {
                logger.warn("The traceparent header has an invalid format: '{}'", traceParentHeader);
                return false;
            }
            if (traceParentHeader.startsWith("ff")) {
                logger.warn("Version ff is not supported");
                return false;
            }
            byte version = HexUtils.getNextByte(traceParentHeader, 0);
            if (version == 0 && traceParentHeader.length() > TEXT_HEADER_EXPECTED_LENGTH) {
                logger.warn("The traceparent header has to be exactly 55 chars long for version 00, but was '{}'", traceParentHeader);
                return false;
            }
            traceId.fromHexString(traceParentHeader, TEXT_HEADER_TRACE_ID_OFFSET);
            if (traceId.isEmpty()) {
                return false;
            }
            parentId.fromHexString(traceParentHeader, TEXT_HEADER_PARENT_ID_OFFSET);
            if (parentId.isEmpty()) {
                return false;
            }
            id.setToRandomValue();
            transactionId.copyFrom(id);
            // TODO don't blindly trust the flags from the caller
            // consider implement rate limiting and/or having a list of trusted sources
            // trace the request if it's either requested or if the parent has recorded it
            flags = HexUtils.getNextByte(traceParentHeader, TEXT_HEADER_FLAGS_OFFSET);
            clock.init();
            return true;
        } catch (IllegalArgumentException e) {
            logger.warn(e.getMessage());
            return false;
        } finally {
            onMutation();
        }
    }

    /**
     * Tries to set trace context identifiers (Id, parent, ...) from traceparent binary header value
     *
     * @param traceParentHeader traceparent binary header value
     * @return {@literal true} if header value is valid, {@literal false} otherwise
     */
    boolean asChildOf(byte[] traceParentHeader) {
        if (logger.isTraceEnabled()) {
            logger.trace("Binary header content UTF-8-decoded: {}", new String(traceParentHeader, StandardCharsets.UTF_8));
        }
        try {
            if (traceParentHeader.length < BINARY_FORMAT_EXPECTED_LENGTH) {
                logger.warn("The traceparent header has to be at least 29 bytes long, but is not");
                return false;
            }
            // Current spec says: "Note, that parsing should not treat any additional bytes in the end of the buffer
            // as an invalid status. Those fields can be added for padding purposes.", which means there is no upper
            // limit. In addition, no version is specified as erroneous, so version is non-informative.

            byte fieldId = traceParentHeader[BINARY_FORMAT_TRACE_ID_OFFSET];
            if (fieldId != BINARY_FORMAT_TRACE_ID_FIELD_ID) {
                logger.warn("Wrong trace-id field identifier: {}", fieldId);
                return false;
            }
            traceId.fromBytes(traceParentHeader, BINARY_FORMAT_TRACE_ID_OFFSET + 1);
            if (traceId.isEmpty()) {
                return false;
            }
            fieldId = traceParentHeader[BINARY_FORMAT_PARENT_ID_OFFSET];
            if (fieldId != BINARY_FORMAT_PARENT_ID_FIELD_ID) {
                logger.warn("Wrong parent-id field identifier: {}", fieldId);
                return false;
            }
            parentId.fromBytes(traceParentHeader, BINARY_FORMAT_PARENT_ID_OFFSET + 1);
            if (parentId.isEmpty()) {
                return false;
            }
            id.setToRandomValue();
            transactionId.copyFrom(id);
            fieldId = traceParentHeader[BINARY_FORMAT_FLAGS_OFFSET];
            if (fieldId != BINARY_FORMAT_FLAGS_FIELD_ID) {
                logger.warn("Wrong flags field identifier: {}", fieldId);
                return false;
            }
            // TODO don't blindly trust the flags from the caller
            // consider implement rate limiting and/or having a list of trusted sources
            // trace the request if it's either requested or if the parent has recorded it
            flags = traceParentHeader[BINARY_FORMAT_FLAGS_OFFSET + 1];
            clock.init();
            return true;
        } catch (IllegalArgumentException e) {
            logger.warn(e.getMessage());
            return false;
        } finally {
            onMutation();
        }
    }

    private boolean noDashAtPosition(String traceParentHeader, int index) {
        return traceParentHeader.charAt(index) != '-';
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

    public void asChildOf(TraceContext parent) {
        traceId.copyFrom(parent.traceId);
        parentId.copyFrom(parent.id);
        transactionId.copyFrom(parent.transactionId);
        flags = parent.flags;
        id.setToRandomValue();
        clock.init(parent.clock);
        serviceName = parent.serviceName;
        applicationClassLoader = parent.applicationClassLoader;
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
        applicationClassLoader = null;
        traceState.resetState();
        traceState.setSizeLimit(coreConfiguration.getTracestateSizeLimit());
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
    <C> void propagateTraceContext(C carrier, TextHeaderSetter<C> headerSetter) {
        String outgoingTraceParent = getOutgoingTraceParentTextHeader().toString();

        headerSetter.setHeader(W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME, outgoingTraceParent, carrier);
        if (coreConfiguration.isElasticTraceparentHeaderEnabled()) {
            headerSetter.setHeader(ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME, outgoingTraceParent, carrier);
        }

        String outgoingTraceState = traceState.toTextHeader();
        if (outgoingTraceState != null) {
            headerSetter.setHeader(TRACESTATE_HEADER_NAME, outgoingTraceState, carrier);
        }
        logger.trace("Trace context headers added to {}", carrier);
    }

    /**
     * Sets Trace context binary headers, using this context as parent, on the provided carrier using the provided setter
     *
     * @param carrier      the binary headers carrier
     * @param headerSetter a setter implementing the actual addition of headers to the headers carrier
     * @param <C>          the header carrier type, for example - a Kafka record
     * @return true if Trace Context headers were set; false otherwise
     */
    <C> boolean propagateTraceContext(C carrier, BinaryHeaderSetter<C> headerSetter) {
        byte[] buffer = headerSetter.getFixedLengthByteArray(TRACE_PARENT_BINARY_HEADER_NAME, BINARY_FORMAT_EXPECTED_LENGTH);
        if (buffer == null || buffer.length != BINARY_FORMAT_EXPECTED_LENGTH) {
            logger.warn("Header setter {} failed to provide a byte buffer with the proper length. Allocating a buffer for each header.",
                headerSetter.getClass().getName());
            buffer = new byte[BINARY_FORMAT_EXPECTED_LENGTH];
        }
        boolean headerBufferFilled = fillOutgoingTraceParentBinaryHeader(buffer);
        if (headerBufferFilled) {
            headerSetter.setHeader(TRACE_PARENT_BINARY_HEADER_NAME, buffer, carrier);
        }
        return headerBufferFilled;
    }

    /**
     * @return  the value of the {@code traceparent} header for downstream services.
     */
    StringBuilder getOutgoingTraceParentTextHeader() {
        if (outgoingTextHeader.length() == 0) {
            // for unsampled traces, propagate the ID of the transaction in calls to downstream services
            // such that the parentID of those transactions point to a transaction that exists
            // remember that we do report unsampled transactions
            fillTraceParentHeader(outgoingTextHeader, isSampled() ? id : transactionId);
        }
        return outgoingTextHeader;
    }

    private void fillTraceParentHeader(StringBuilder sb, Id spanId) {
        sb.append("00-");
        traceId.writeAsHex(sb);
        sb.append('-');
        spanId.writeAsHex(sb);
        sb.append('-');
        HexUtils.writeByteAsHex(flags, sb);
    }

    /**
     * Fills the given byte array with a binary representation of the {@code traceparent} header for downstream services.
     *
     * @param buffer buffer to fill
     * @return true if buffer was filled, false otherwise
     */
    private boolean fillOutgoingTraceParentBinaryHeader(byte[] buffer) {
        if (buffer.length < BINARY_FORMAT_EXPECTED_LENGTH) {
            logger.warn("Given byte array does not have the minimal required length - {}", BINARY_FORMAT_EXPECTED_LENGTH);
            return false;
        }
        buffer[0] = BINARY_FORMAT_CURRENT_VERSION;
        buffer[BINARY_FORMAT_TRACE_ID_OFFSET] = BINARY_FORMAT_TRACE_ID_FIELD_ID;
        traceId.toBytes(buffer, BINARY_FORMAT_TRACE_ID_OFFSET + 1);
        buffer[BINARY_FORMAT_PARENT_ID_OFFSET] = BINARY_FORMAT_PARENT_ID_FIELD_ID;
        // for unsampled traces, propagate the ID of the transaction in calls to downstream services
        // such that the parentID of those transactions point to a transaction that exists
        // remember that we do report unsampled transactions
        Id parentId = isSampled() ? id : transactionId;
        parentId.toBytes(buffer, BINARY_FORMAT_PARENT_ID_OFFSET + 1);
        buffer[BINARY_FORMAT_FLAGS_OFFSET] = BINARY_FORMAT_FLAGS_FIELD_ID;
        buffer[BINARY_FORMAT_FLAGS_OFFSET + 1] = flags;
        return true;
    }

    public boolean isChildOf(TraceContext other) {
        return other.getTraceId().equals(traceId) && other.getId().equals(parentId);
    }

    public boolean hasContent() {
        return !id.isEmpty();
    }

    public void copyFrom(TraceContext other) {
        traceId.copyFrom(other.traceId);
        id.copyFrom(other.id);
        parentId.copyFrom(other.parentId);
        transactionId.copyFrom(other.transactionId);
        flags = other.flags;
        discardable = other.discardable;
        clock.init(other.clock);
        serviceName = other.serviceName;
        applicationClassLoader = other.applicationClassLoader;
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
     * Overrides the {@code co.elastic.apm.agent.impl.payload.Service#name} property sent via the meta data Intake V2 event.
     *
     * @param serviceName the service name for this event
     */
    public void setServiceName(@Nullable String serviceName) {
        this.serviceName = serviceName;
    }

    public Span createSpan() {
        return tracer.startSpan(fromParentContext(), this);
    }

    public Span createSpan(long epochMicros) {
        return tracer.startSpan(fromParentContext(), this, epochMicros);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TraceContext that = (TraceContext) o;
        return id.equals(that.id) &&
            traceId.equals(that.traceId);
    }

    public boolean idEquals(@Nullable TraceContext o) {
        if (this == o) return true;
        if (o == null) return false;
        return id.equals(o.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(traceId, id, parentId, flags);
    }

    void setApplicationClassLoader(@Nullable ClassLoader classLoader) {
        if (ClassLoaderUtils.isBootstrapClassLoader(classLoader) || ClassLoaderUtils.isAgentClassLoader(classLoader)) {
            return;
        }
        WeakReference<ClassLoader> local = classLoaderWeakReferenceCache.get(classLoader);
        if (local == null) {
            local = new WeakReference<>(classLoader);
            classLoaderWeakReferenceCache.putIfAbsent(classLoader, local);
        }
        applicationClassLoader = local;
    }

    @Nullable
    public ClassLoader getApplicationClassLoader() {
        if (applicationClassLoader != null) {
            return applicationClassLoader.get();
        } else {
            return null;
        }
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
        buffer[offset++] = flags;
        buffer[offset++] = (byte) (discardable ? 1 : 0);
        ByteUtils.putLong(buffer, offset, clock.getOffset());
    }

    private void asChildOf(byte[] buffer, @Nullable String serviceName) {
        int offset = 0;
        offset += traceId.fromBytes(buffer, offset);
        offset += parentId.fromBytes(buffer, offset);
        offset += transactionId.fromBytes(buffer, offset);
        id.setToRandomValue();
        flags = buffer[offset++];
        discardable = buffer[offset++] == (byte) 1;
        clock.init(ByteUtils.getLong(buffer, offset));
        this.serviceName = serviceName;
        onMutation();
    }

    public void deserialize(byte[] buffer, @Nullable String serviceName) {
        int offset = 0;
        offset += traceId.fromBytes(buffer, offset);
        offset += id.fromBytes(buffer, offset);
        offset += transactionId.fromBytes(buffer, offset);
        flags = buffer[offset++];
        discardable = buffer[offset++] == (byte) 1;
        clock.init(ByteUtils.getLong(buffer, offset));
        this.serviceName = serviceName;
        onMutation();
    }

    public static void deserializeSpanId(Id id, byte[] buffer) {
        id.fromBytes(buffer, 16);
    }

    public static long getSpanId(byte[] serializedTraceContext) {
        return ByteUtils.getLong(serializedTraceContext, 16);
    }

    public boolean traceIdAndIdEquals(byte[] serialized) {
        return id.dataEquals(serialized, traceId.getLength()) && traceId.dataEquals(serialized, 0);
    }

    public byte getFlags() {
        return flags;
    }

    public interface ChildContextCreator<T> {
        boolean asChildOf(TraceContext child, T parent);
    }

    public interface ChildContextCreatorTwoArg<T, A> {
        boolean asChildOf(TraceContext child, @Nullable T parent, A arg);
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
}
