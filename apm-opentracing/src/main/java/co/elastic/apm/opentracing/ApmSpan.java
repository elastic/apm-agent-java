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
package co.elastic.apm.opentracing;

import io.opentracing.Span;
import io.opentracing.log.Fields;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Map;

class ApmSpan implements Span {

    @Nullable
    private final TraceContextSpanContext spanContext;
    @Nullable
    // co.elastic.apm.impl.transaction.AbstractSpan in case of unfinished spans
    private Object dispatcher;

    ApmSpan(@Nullable Object dispatcher) {
        this.dispatcher = dispatcher;
        this.spanContext = new TraceContextSpanContext(getTraceContext(dispatcher));
    }

    ApmSpan(@Nonnull TraceContextSpanContext spanContext) {
        this.spanContext = spanContext;
    }

    @Nullable
    private Object getTraceContext(@Nullable Object dispatcher) {
        // co.elastic.apm.opentracing.impl.ApmSpanInstrumentation$GetTraceContextInstrumentation
        return null;
    }

    @Override
    public TraceContextSpanContext context() {
        return spanContext;
    }

    @Override
    public ApmSpan setTag(String key, String value) {
        handleTag(key, value);
        return this;
    }

    @Override
    public ApmSpan setTag(String key, boolean value) {
        handleTag(key, value);
        return this;
    }

    @Override
    public ApmSpan setTag(String key, Number value) {
        handleTag(key, value);
        return this;
    }

    @Override
    public ApmSpan setOperationName(String operationName) {
        // co.elastic.apm.opentracing.impl.ApmSpanInstrumentation$SetOperationName
        return this;
    }

    @Override
    public void finish() {
        finish(-1);
    }

    @Override
    public void finish(long finishMicros) {
        if (spanContext != null) {
            final Object traceContext = spanContext.getTraceContext();
            if (traceContext != null) {
                // prevents race conditions with ScopeManager#activate(Span)
                synchronized (traceContext) {
                    finishInternal(finishMicros);
                }
            }
        }
    }

    private void finishInternal(long finishMicros) {
        // implementation injected at runtime by co.elastic.apm.opentracing.impl.ApmSpanInstrumentation.FinishInstrumentation.finishInternal
    }

    @Nullable
    Object getSpan() {
        return dispatcher;
    }

    @Override
    public ApmSpan log(String event) {
        log(Collections.singletonMap(Fields.EVENT, event));
        return this;
    }

    @Override
    public ApmSpan log(Map<String, ?> fields) {
        return log(-1, fields);
    }

    @Override
    public ApmSpan log(long timestampMicroseconds, String event) {
        log(timestampMicroseconds, Collections.singletonMap(Fields.EVENT, event));
        return this;
    }

    @Override
    public ApmSpan log(long timestampMicroseconds, Map<String, ?> fields) {
        // co.elastic.apm.opentracing.impl.ApmSpanInstrumentation.LogInstrumentation
        return this;
    }

    @Override
    public ApmSpan setBaggageItem(String key, String value) {
        return this;
    }

    @Override
    @Nullable
    public String getBaggageItem(String key) {
        for (Map.Entry<String, String> baggage : context().baggageItems()) {
            if (baggage.getKey().equals(key)) {
                return baggage.getValue();
            }
        }
        return null;
    }

    private void handleTag(String key, @Nullable Object value) {
        // implementation injected at runtime by co.elastic.apm.opentracing.impl.ApmSpanInstrumentation.TagInstrumentation.handleTag
    }

    @Override
    public String toString() {
        return String.valueOf(dispatcher);
    }

}
