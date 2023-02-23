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
package co.elastic.apm.agent.sdk.utils;

import co.elastic.apm.tracer.api.*;
import co.elastic.apm.tracer.api.dispatch.*;

import javax.annotation.Nullable;

public class TraceContextUtil {

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
                state.addTraceState(tracestateHeaderValue);
            }
        }
    };
    private static final HeaderChildContextCreator FROM_TRACE_CONTEXT_TEXT_HEADERS =
        new HeaderChildContextCreator<String, Object>() {
            @Override
            public boolean asChildOf(TraceContext child, @Nullable Object carrier, HeaderGetter<String, Object> traceContextHeaderGetter) {
                if (carrier == null) {
                    return false;
                }

                boolean isValid = false;
                String traceparent = traceContextHeaderGetter.getFirstHeader(TraceContext.W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME, carrier);
                if (traceparent != null) {
                    isValid = child.asChildOf(traceparent);
                }

                if (!isValid) {
                    // Look for the legacy Elastic traceparent header (in case this comes from an older agent)
                    traceparent = traceContextHeaderGetter.getFirstHeader(TraceContext.ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME, carrier);
                    if (traceparent != null) {
                        isValid = child.asChildOf(traceparent);
                    }
                }

                if (isValid) {
                    // as per spec, the tracestate header can be multi-valued
                    traceContextHeaderGetter.forEach(TraceContext.TRACESTATE_HEADER_NAME, carrier, child, TRACESTATE_HEADER_CONSUMER);
                }

                return isValid;
            }
        };
    private static final HeaderChildContextCreator FROM_TRACE_CONTEXT_BINARY_HEADERS =
        new HeaderChildContextCreator<byte[], Object>() {
            @Override
            public boolean asChildOf(TraceContext child, @Nullable Object carrier, HeaderGetter<byte[], Object> traceContextHeaderGetter) {
                if (carrier == null) {
                    return false;
                }
                byte[] traceparent = traceContextHeaderGetter.getFirstHeader(TraceContext.TRACE_PARENT_BINARY_HEADER_NAME, carrier);
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

    private TraceContextUtil() {
    }

    @SuppressWarnings("unchecked")
    public static <C> HeaderChildContextCreator<String, C> getFromTraceContextTextHeaders() {
        return (HeaderChildContextCreator<String, C>) FROM_TRACE_CONTEXT_TEXT_HEADERS;
    }

    @SuppressWarnings("unchecked")
    public static <C> HeaderChildContextCreator<byte[], C> getFromTraceContextBinaryHeaders() {
        return (HeaderChildContextCreator<byte[], C>) FROM_TRACE_CONTEXT_BINARY_HEADERS;
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

    public static <S, D> void copyTraceContextTextHeaders(S source, TextHeaderGetter<S> headerGetter, D destination, TextHeaderSetter<D> headerSetter) {
        String w3cApmTraceParent = headerGetter.getFirstHeader(TraceContext.W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME, source);
        if (w3cApmTraceParent != null) {
            headerSetter.setHeader(TraceContext.W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME, w3cApmTraceParent, destination);
        }
        String elasticApmTraceParent = headerGetter.getFirstHeader(TraceContext.ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME, source);
        if (elasticApmTraceParent != null) {
            headerSetter.setHeader(TraceContext.ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME, elasticApmTraceParent, destination);
        }
        String tracestate = headerGetter.getFirstHeader(TraceContext.TRACESTATE_HEADER_NAME, source);
        if (tracestate != null) {
            headerSetter.setHeader(TraceContext.TRACESTATE_HEADER_NAME, tracestate, destination);
        }
    }

    public static <C> boolean containsTraceContextTextHeaders(C carrier, TextHeaderGetter<C> headerGetter) {
        return headerGetter.getFirstHeader(TraceContext.W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME, carrier) != null;
    }

    public static <C> void removeTraceContextHeaders(C carrier, HeaderRemover<C> headerRemover) {
        headerRemover.remove(TraceContext.W3C_TRACE_PARENT_TEXTUAL_HEADER_NAME, carrier);
        headerRemover.remove(TraceContext.ELASTIC_TRACE_PARENT_TEXTUAL_HEADER_NAME, carrier);
        headerRemover.remove(TraceContext.TRACESTATE_HEADER_NAME, carrier);
        headerRemover.remove(TraceContext.TRACE_PARENT_BINARY_HEADER_NAME, carrier);
    }
}
