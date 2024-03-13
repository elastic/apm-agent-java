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
package co.elastic.apm.agent.opentelemetry.tracing;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.baggage.BaggageImpl;
import co.elastic.apm.agent.impl.transaction.AbstractSpanImpl;
import co.elastic.apm.agent.impl.transaction.TraceStateImpl;
import co.elastic.apm.agent.opentelemetry.baggage.OtelBaggage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Bridge implementation of OpenTelemetry {@link Context} that allows to provide compatibility with {@link TraceStateImpl}.
 */
public class OTelBridgeContext extends TraceStateImpl<OTelBridgeContext> implements Context, Scope {

    /**
     * Original root context as returned by {@link Context#root()} before instrumentation.
     */
    @Nullable
    private static volatile Context originalRootContext;

    /**
     * Bridged root context that will be returned by {@link Context#root()} after instrumentation
     */
    @Nullable
    private static volatile OTelBridgeContext root;

    /**
     * OTel context used for key/value storage
     */
    private final Context otelContext;

    private OTelBridgeContext(ElasticApmTracer tracer, Context otelContext) {
        super(tracer);
        this.otelContext = otelContext;
    }

    /**
     * Captures the original root context and sets-up the bridged root if required
     *
     * @param tracer       tracer
     * @param originalRoot original OTel root context
     * @return bridged context
     */
    public static OTelBridgeContext bridgeRootContext(ElasticApmTracer tracer, Context originalRoot) {
        if (root != null) {
            return root;
        }

        synchronized (OTelBridgeContext.class) {
            if (root == null) {
                originalRootContext = originalRoot;
                root = new OTelBridgeContext(tracer, originalRoot);
            }
        }
        return root;
    }

    /**
     * Bridges an active elastic span to an active OTel span context
     *
     * @param tracer         tracer
     * @param currentContext elastic (currently active) context
     * @return bridged context with span as active
     */
    public static OTelBridgeContext wrapElasticActiveSpan(ElasticApmTracer tracer, TraceStateImpl<?> currentContext) {
        if (root == null) {
            // Ensure that root context is being accessed at least once to capture the original root
            // OTel 1.0 directly calls ArrayBasedContext.root() which is not publicly accessible, later versions delegate
            // to ContextStorage.root() which we can't call from here either.
            Context.root();
        }
        Objects.requireNonNull(originalRootContext, "OTel original context must be set through bridgeRootContext first");

        Context result = originalRootContext;
        if (currentContext.getSpan() != null) {
            result = result.with(new OTelSpan(currentContext.getSpan()));
        }
        if (!currentContext.getBaggage().isEmpty()) {
            result = result.with(OtelBaggage.fromElasticBaggage(currentContext.getBaggage()));
        }

        return new OTelBridgeContext(tracer, result);
    }

    @Nullable
    @Override
    public AbstractSpanImpl<?> getSpan() {
        // get otel span from context
        Span span = Span.fromContext(otelContext);
        if (span instanceof OTelSpan) {
            return ((OTelSpan) span).getInternalSpan();
        }
        return null;
    }

    @Override
    public BaggageImpl getBaggage() {
        io.opentelemetry.api.baggage.Baggage otelBaggage = io.opentelemetry.api.baggage.Baggage.fromContext(otelContext);

        if (otelBaggage == null || otelBaggage.isEmpty()) {
            return BaggageImpl.EMPTY;
        }
        return OtelBaggage.toElasticBaggage(otelBaggage);
    }


    // OTel context implementation

    @Nullable
    @Override
    public <V> V get(ContextKey<V> key) {
        return otelContext.get(key);
    }

    @Override
    public <V> Context with(ContextKey<V> key, V value) {
        return new OTelBridgeContext(tracer, otelContext.with(key, value));
    }

    // OTel scope implementation

    @Override
    public void close() {
        deactivate();
    }

    @Override
    public String toString() {
        return "OTelBridgeContext[" + otelContext + "]";
    }

    @Override
    public boolean isEmpty() {
        return this == root; //we only know that the root context is empty, other contexts could have any kind of keys
    }

    @Override
    public void incrementReferences() {
        //No need for reference counting: the contained span is always kept alive by the wrapping OTelSpan
    }

    @Override
    public void decrementReferences() {
        //No need for reference counting: the contained span is always kept alive by the wrapping OTelSpan
    }
}
