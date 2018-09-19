/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.impl.transaction;

import co.elastic.apm.impl.sampling.Sampler;
import co.elastic.apm.objectpool.Recyclable;
import co.elastic.apm.util.HexUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is an implementation of the
 * <a href="https://w3c.github.io/distributed-tracing/report-trace-context.html#traceparent-field">w3c traceparent header draft</a>.
 * <p>
 * As this is just a draft at the moment,
 * we don't use the official header name but prepend the custom prefix {@code Elastic-Apm-}.
 * </p>
 *
 * <pre>
 * Elastic-Apm-Traceparent: 00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01
 * (_____________________)  () (______________________________) (______________) ()
 *            v             v                 v                        v         v
 *       Header name     Version           Trace-Id                Span-Id     Flags
 * </pre>
 */
public class TraceContext implements Recyclable {

    public static final String TRACE_PARENT_HEADER = "Elastic-Apm-Traceparent";
    private static final Logger logger = LoggerFactory.getLogger(TraceContext.class);
    private static final int TRACE_PARENT_LENGTH = 55;
    // ???????1 -> requested
    // ???????0 -> not requested
    private static final byte FLAG_REQUESTED = 0b0000_0001;
    // ??????1? -> maybe recorded
    // ??????0? -> not recorded
    private static final byte FLAG_RECORDED = 0b0000_0010;
    private final Id traceId = Id.new128BitId();
    private final Id id;
    private final Id parentId = Id.new64BitId();
    private final Id transactionId = Id.new64BitId();
    private final StringBuilder outgoingHeader = new StringBuilder(TRACE_PARENT_LENGTH);
    private byte flags;

    /**
     * Creates a new {@code traceparent}-compliant {@link TraceContext} with a 64 bit {@link #id}.
     * <p>
     * Note: the {@link #traceId} will still be 128 bit
     * </p>
     */
    public static TraceContext with64BitId() {
        return new TraceContext(Id.new64BitId());
    }

    /**
     * Creates a new {@link TraceContext} with a 128 bit {@link #id},
     * suitable for errors,
     * as those might not have a trace reference and therefore require a larger id in order to be globally unique.
     */
    public static TraceContext with128BitId() {
        return new TraceContext(Id.new128BitId());
    }

    private TraceContext(Id id) {
        this.id = id;
    }

    public boolean asChildOf(String traceParentHeader) {
        try {
            if (traceParentHeader.length() != 55) {
                logger.warn("The traceparent header has to be exactly 55 chars long, but was '{}'", traceParentHeader);
                return false;
            }
            if (!traceParentHeader.startsWith("00-")) {
                logger.warn("Only version 00 of the traceparent header is supported, but was '{}'", traceParentHeader);
                return false;
            }
            parseTraceId(traceParentHeader);
            if (traceId.isEmpty()) {
                return false;
            }
            parseParentId(traceParentHeader);
            if (parentId.isEmpty()) {
                return false;
            }
            id.setToRandomValue();
            transactionId.copyFrom(id);
            flags = getTraceOptions(traceParentHeader);
            // TODO don't blindly trust the flags from the caller
            // consider implement rate limiting and/or having a list of trusted sources
            // trace the request if it's either requested or if the parent has recorded it
            if (isRequested()) {
                setRecorded(true);
            }
            return true;
        } catch (IllegalArgumentException e) {
            logger.warn(e.getMessage());
            return false;
        }
    }

    public void asRootSpan(Sampler sampler) {
        traceId.setToRandomValue();
        id.setToRandomValue();
        transactionId.copyFrom(id);
        if (sampler.isSampled(traceId)) {
            this.flags = FLAG_RECORDED | FLAG_REQUESTED;
        }
    }

    public void asChildOf(TraceContext parent) {
        traceId.copyFrom(parent.traceId);
        parentId.copyFrom(parent.id);
        transactionId.copyFrom(parent.transactionId);
        flags = parent.flags;
        id.setToRandomValue();
    }

    private void parseTraceId(String traceParent) {
        traceId.fromHexString(traceParent, 3);
    }

    private void parseParentId(String traceParent) {
        parentId.fromHexString(traceParent, 36);
    }

    private byte getTraceOptions(String traceParent) {
        return HexUtils.getNextByte(traceParent, 53);
    }

    @Override
    public void resetState() {
        traceId.resetState();
        id.resetState();
        parentId.resetState();
        outgoingHeader.setLength(0);
        flags = 0;
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

    public boolean isSampled() {
        return isRecorded();
    }

    /**
     * When {@code true}, recommends the request should be traced.
     * A caller who defers a tracing decision leaves this
     * flag unset.
     */
    boolean isRequested() {
        return (flags & FLAG_REQUESTED) == FLAG_REQUESTED;
    }

    /**
     * When {@code true}, documents that the caller may have recorded trace data.
     * A caller who does not record trace data out-of-band leaves this flag unset.
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

    void setRequested(boolean requested) {
        if (requested) {
            flags |= FLAG_REQUESTED;
        } else {
            flags &= ~FLAG_REQUESTED;
        }
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
            fillTraceParentHeader(outgoingHeader, id);
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

    public boolean isChildOf(TraceContext parent) {
        return parent.getTraceId().equals(traceId) && parent.getId().equals(parentId);
    }

    public boolean hasContent() {
        return !traceId.isEmpty() && !parentId.isEmpty() && !id.isEmpty();
    }
}
