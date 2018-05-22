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
package co.elastic.apm.opentracing;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.log.Fields;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Map;

class ApmSpan implements Span, SpanContext {

    @Nullable
    // co.elastic.apm.impl.transaction.Transaction
    private final Object transaction;
    @Nullable
    // co.elastic.apm.impl.transaction
    private final Object span;

    ApmSpan(@Nullable Object transaction, @Nullable Object span) {
        this.transaction = transaction;
        this.span = span;
    }

    @Override
    public SpanContext context() {
        return this;
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
        return this;
    }

    @Override
    public void finish() {
        finishInternal(System.nanoTime());
    }

    @Override
    public void finish(long finishMicros) {
        finishInternal(finishMicros);
    }

    private void finishInternal(long finishMicros) {
        // implementation injected at runtime by co.elastic.apm.opentracing.impl.ApmSpanInstrumentation.FinishInstrumentation.finishInternal
    }

    @Nullable
    Object getSpan() {
        return span;
    }

    @Nullable
    Object getTransaction() {
        return transaction;
    }

    @Override
    public ApmSpan log(Map<String, ?> fields) {
        if ("error".equals(fields.get(Fields.EVENT))) {
            createError(System.currentTimeMillis(), fields);
        }
        return this;
    }

    @Override
    public ApmSpan log(long timestampMicroseconds, Map<String, ?> fields) {
        if ("error".equals(fields.get(Fields.EVENT))) {
            createError(timestampMicroseconds / 1000, fields);
        }
        return this;
    }

    private void createError(long epochTimestampMillis, Map<String, ?> fields) {
    }

    @Override
    public ApmSpan log(String event) {
        log(Collections.singletonMap(Fields.EVENT, event));
        return this;
    }

    @Override
    public ApmSpan log(long timestampMicroseconds, String event) {
        log(timestampMicroseconds, Collections.singletonMap(Fields.EVENT, event));
        return this;
    }

    @Override
    public ApmSpan setBaggageItem(String key, String value) {
        return this;
    }

    @Override
    @Nullable
    public String getBaggageItem(String key) {
        return null;
    }

    @Override
    public Iterable<Map.Entry<String, String>> baggageItems() {
        return Collections.emptyList();
    }


    private void handleTag(String key, @Nullable Object value) {
        // implementation injected at runtime by co.elastic.apm.opentracing.impl.ApmSpanInstrumentation.TagInstrumentation.handleTag
    }

}
