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
    // ???????1 -> maybe recorded
    // ???????0 -> not recorded
    private static final byte FLAG_RECORDED = 0b0000_0010;
    private final TraceId traceId = new TraceId();
    private final SpanId id = new SpanId();
    private final SpanId parentId = new SpanId();
    private final StringBuilder outgoingHeader = new StringBuilder(TRACE_PARENT_LENGTH);
    private byte flags;

    public void asChildOf(String traceParentHeader) {
        if (traceParentHeader.length() != 55) {
            logger.warn("The traceparent header has to be exactly 55 chars long, but was '{}'", traceParentHeader);
            return;
        }
        if (!traceParentHeader.startsWith("00-")) {
            logger.warn("Only version 00 of the traceparent header is supported, but was '{}'", traceParentHeader);
            return;
        }
        parseTraceId(traceParentHeader);
        parseParentId(traceParentHeader);
        id.setToRandomValue();
        flags = getTraceOptions(traceParentHeader);
        // TODO don't blindly trust the flags from the caller
        // consider implement rate limiting and/or having a list of trusted sources
        // trace the request if it's either requested or if the parent has recorded it
        if (isRequested()) {
            setRecorded(true);
        }
    }

    public void asRootSpan(Sampler sampler) {
        traceId.setToRandomValue();
        id.setToRandomValue();
        if (sampler.isSampled(traceId)) {
            this.flags = FLAG_RECORDED | FLAG_REQUESTED;
        }
    }

    public void asChildOf(TraceContext traceContext) {
        traceId.copyFrom(traceContext.traceId);
        parentId.copyFrom(traceContext.id);
        id.setToRandomValue();
    }

    private void parseTraceId(String traceParent) {
        HexUtils.nextBytes(traceParent, 3, traceId.getBytes());
    }

    private void parseParentId(String traceParent) {
        HexUtils.nextBytes(traceParent, 36, parentId.getBytes());
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
    public TraceId getTraceId() {
        return traceId;
    }

    public SpanId getId() {
        return id;
    }

    /**
     * The ID of the caller span (parent)
     *
     * @return the parent id
     */
    public SpanId getParentId() {
        return parentId;
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

    private void fillTraceParentHeader(StringBuilder sb, SpanId spanId) {
        sb.append("00-");
        HexUtils.writeBytesAsHex(traceId.getBytes(), sb);
        sb.append('-');
        HexUtils.writeBytesAsHex(spanId.getBytes(), sb);
        sb.append('-');
        HexUtils.writeByteAsHex(flags, sb);
    }

    public boolean isChildOf(TraceContext parent) {
        return parent.getTraceId().equals(traceId) && parent.getId().equals(parentId);
    }

    public boolean hasContent() {
        return !traceId.isEmpty() && parentId.asLong() != 0 && id.asLong() != 0;
    }
}
