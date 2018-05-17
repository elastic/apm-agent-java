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

import co.elastic.apm.impl.ElasticApmTracer;
import co.elastic.apm.impl.sampling.ConstantSampler;
import co.elastic.apm.impl.sampling.Sampler;
import co.elastic.apm.impl.transaction.Transaction;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

class ApmSpanBuilder implements Tracer.SpanBuilder {
    private static final Logger logger = LoggerFactory.getLogger(ApmSpanBuilder.class);

    @Nullable
    private final String operationName;
    private final ElasticApmTracer tracer;
    private final Map<String, Object> tags = new HashMap<>();
    private final ApmScopeManager scopeManager;
    @Nullable
    private ApmSpan parent;
    private long nanoTime = System.nanoTime();
    private boolean ignoreActiveSpan = false;
    private Sampler sampler;
    @Nullable
    private String traceParentHeader;

    ApmSpanBuilder(@Nullable String operationName, ElasticApmTracer tracer, ApmScopeManager scopeManager) {
        this.operationName = operationName;
        this.tracer = tracer;
        this.scopeManager = scopeManager;
        sampler = tracer.getSampler();
    }

    @Override
    public ApmSpanBuilder asChildOf(SpanContext parent) {
        final ApmSpanContext parenApmContext = (ApmSpanContext) parent;
        if (parenApmContext instanceof ApmSpan) {
            asChildOf((Span) parenApmContext);
        } else if (parent != null) {
            this.traceParentHeader = parenApmContext.getTraceParentHeader();
        }
        return this;
    }

    @Override
    public ApmSpanBuilder asChildOf(Span parent) {
        this.parent = (ApmSpan) parent;
        return this;
    }

    @Override
    public ApmSpanBuilder addReference(String referenceType, SpanContext referencedContext) {
        // TODO add reference types
        return this;
    }

    @Override
    public ApmSpanBuilder ignoreActiveSpan() {
        this.ignoreActiveSpan = true;
        return this;
    }

    @Override
    public ApmSpanBuilder withTag(String key, String value) {
        tags.put(key, value);
        return this;
    }

    @Override
    public ApmSpanBuilder withTag(String key, boolean value) {
        tags.put(key, value);
        return this;
    }

    @Override
    public ApmSpanBuilder withTag(String key, Number value) {
        if (Tags.SAMPLING_PRIORITY.getKey().equals(key) && value != null) {
            sampler = ConstantSampler.of(value.intValue() > 0);
        } else {
            tags.put(key, value);
        }
        return this;
    }

    @Override
    public ApmSpanBuilder withStartTimestamp(long microseconds) {
        this.nanoTime = microseconds * 1000;
        return this;
    }

    @Override
    public ApmScope startActive(boolean finishSpanOnClose) {
        return scopeManager.activate(startApmSpan(), finishSpanOnClose);
    }

    @Override
    public ApmSpan start() {
        return startApmSpan();
    }

    @Override
    @Deprecated
    public ApmSpan startManual() {
        return start();
    }

    private ApmSpan startApmSpan() {
        final ApmScope active = scopeManager.active();
        if (!ignoreActiveSpan && active != null) {
            asChildOf((Span) active.span());
        }
        final ApmSpan apmSpan;
        if (parent == null) {
            final Transaction transaction;
            if (Tags.SPAN_KIND_CLIENT.equals(tags.get(Tags.SPAN_KIND.getKey()))) {
                logger.info("Ignoring transaction '{}', as a span.kind client can never be a transaction. " +
                    "Consider creating a span for the whole request.", operationName);
                transaction = tracer.noopTransaction();
            } else {
                transaction = tracer.startManualTransaction(traceParentHeader, sampler, nanoTime);
            }
            apmSpan = new ApmSpan(transaction, null, tracer).setOperationName(operationName);
        } else {
            Transaction transaction = getTransaction(parent);
            final co.elastic.apm.impl.transaction.Span span = tracer.startManualSpan(transaction, parent.getSpan(), nanoTime);
            apmSpan = new ApmSpan(null, span, tracer).setOperationName(operationName);
        }
        addTags(apmSpan);
        return apmSpan;
    }

    private void addTags(ApmSpan apmSpan) {
        for (Map.Entry<String, Object> entry : tags.entrySet()) {
            if (entry.getValue() instanceof String) {
                apmSpan.setTag(entry.getKey(), (String) entry.getValue());
            } else if (entry.getValue() instanceof Number) {
                apmSpan.setTag(entry.getKey(), (Number) entry.getValue());
            } else if (entry.getValue() instanceof Boolean) {
                apmSpan.setTag(entry.getKey(), (Boolean) entry.getValue());
            }
        }
    }

    private Transaction getTransaction(ApmSpan parent) {
        final Transaction parentTransaction = parent.getTransaction();
        if (parentTransaction != null) {
            return parentTransaction;
        } else {
            final co.elastic.apm.impl.transaction.Span parentSpan = parent.getSpan();
            assert parentSpan != null;
            final Transaction transaction = parentSpan.getTransaction();
            assert transaction != null;
            return transaction;
        }
    }

}
