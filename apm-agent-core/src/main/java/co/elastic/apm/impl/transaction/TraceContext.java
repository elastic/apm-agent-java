/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 the original author or authors
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

/**
 * This is an implementation of the
 * <a href="https://w3c.github.io/distributed-tracing/report-trace-context.html#traceparent-field">w3c traceparent header draft</a>.
 *
 * <pre>
 * traceparent: 00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01
 * (_________)  () (______________________________) (______________) ()
 *      v       v                 v                        v         v
 * Header name  Version        Trace-Id                Span-Id     Flags
 * </pre>
 */
public class TraceContext implements Recyclable {

    public static final String TRACE_PARENT_HEADER = "traceparent";
    private static final int TRACE_PARENT_LENGTH = 55;
    private static final byte FLAG_SAMPLED = 1;
    private final TraceId traceId = new TraceId();
    private final SpanId id = new SpanId();
    private final SpanId parentId = new SpanId();
    private final StringBuilder outgoingHeader = new StringBuilder(TRACE_PARENT_LENGTH);
    private byte flags;

    public void asChildOf(String traceParentHeader) {
        if (traceParentHeader.length() != 55) {
            throw new IllegalArgumentException("The traceparent header has to be exactly 55 chars long");
        }
        if (!traceParentHeader.startsWith("00-")) {
            throw new IllegalArgumentException("Only version 00 of the traceparent header is supported");
        }
        parseTraceId(traceParentHeader);
        parseParentId(traceParentHeader);
        id.setToRandomValue();
        flags = getTraceOptions(traceParentHeader);
        fillTraceParentHeader(outgoingHeader, id);
    }

    public void asRootSpan(Sampler sampler) {
        traceId.setToRandomValue();
        id.setToRandomValue();
        if (sampler.isSampled(traceId)) {
            this.flags = FLAG_SAMPLED;
        }
    }

    public void asChildOf(TraceContext traceContext) {
        traceId.copyFrom(traceContext.traceId);
        parentId.copyFrom(traceContext.id);
        flags = traceContext.flags;
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
        return (flags & FLAG_SAMPLED) == FLAG_SAMPLED;
    }

    public void setSampled(boolean sampled) {
        if (sampled) {
            flags |= FLAG_SAMPLED;
        } else {
            flags &= ~FLAG_SAMPLED;
        }
    }

    /**
     * Returns the value of the {@code traceparent} header, as it was received.
     *
     * @return
     */
    public String getIncomingTraceParentHeader() {
        final StringBuilder sb = new StringBuilder(TRACE_PARENT_LENGTH);
        fillTraceParentHeader(sb, parentId);
        return sb.toString();
    }

    /**
     * Returns the value of the {@code traceparent} header for downstream services.
     *
     * <p>
     * The difference
     * </p>
     *
     * @return
     */
    public StringBuilder getOutgoingTraceParentHeader() {
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
}
