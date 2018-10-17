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

import io.opentracing.References;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

class ApmSpanBuilder implements Tracer.SpanBuilder {

    @Nullable
    private final String operationName;
    // co.elastic.apm.impl.ElasticApmTracer
    private final Map<String, Object> tags = new HashMap<>();
    private final ApmScopeManager scopeManager;
    @Nullable
    private ApmSpan parent;

    private boolean ignoreActiveSpan = false;
    private long microseconds = -1;
    @Nullable
    private ExternalProcessSpanContext parentContext;

    ApmSpanBuilder(@Nullable String operationName, ApmScopeManager scopeManager) {
        this.operationName = operationName;
        this.scopeManager = scopeManager;
    }

    @Override
    public ApmSpanBuilder asChildOf(SpanContext parent) {
        if (parent instanceof ApmSpan) {
            asChildOf((Span) parent);
        } else if (parent instanceof ExternalProcessSpanContext) {
            this.parentContext = (ExternalProcessSpanContext) parent;
        }
        return this;
    }

    @Override
    public ApmSpanBuilder asChildOf(Span parent) {
        if (parent != null) {
            this.parent = ((ApmSpan) parent);
        }
        return this;
    }

    @Override
    public ApmSpanBuilder addReference(String referenceType, SpanContext referencedContext) {
        if (References.CHILD_OF.equals(referenceType)) {
            asChildOf(referencedContext);
        }
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
        tags.put(key, value);
        return this;
    }

    @Override
    public ApmSpanBuilder withStartTimestamp(long microseconds) {
        this.microseconds = microseconds;
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
        if (!ignoreActiveSpan && parent == null) {
            final ApmScope active = scopeManager.active();
            if (active != null) {
                parent = active.span();
            }
        }
        final Iterable<Map.Entry<String, String>> baggage = parentContext != null ? parentContext.baggageItems() : null;
        final Object dispatcher = createSpan(parent, parent != null ? parent.getSpan() : null, baggage);
        final ApmSpan apmSpan = new ApmSpan(dispatcher).setOperationName(operationName);
        addTags(apmSpan);
        return apmSpan;
    }


    @Nullable
    private Object createSpan(ApmSpan parent, Object apmParent, Iterable<Map.Entry<String, String>> baggage) {
        // co.elastic.apm.opentracing.impl.ApmSpanBuilderInstrumentation.CreateSpanInstrumentation.createSpan
        return null;
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

}
