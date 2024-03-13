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

import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.common.util.WildcardMatcher;
import co.elastic.apm.agent.configuration.CoreConfigurationImpl;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.ElasticApmTracerBuilder;
import co.elastic.apm.agent.impl.TextHeaderMapAccessor;
import co.elastic.apm.agent.impl.baggage.BaggageContext;
import co.elastic.apm.agent.impl.context.TransactionContextImpl;
import co.elastic.apm.agent.impl.error.ErrorCaptureImpl;
import co.elastic.apm.agent.objectpool.TestObjectPoolFactory;
import co.elastic.apm.agent.tracer.TraceState;
import co.elastic.apm.agent.tracer.Scope;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.stagemonitor.configuration.ConfigurationRegistry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static co.elastic.apm.agent.testutils.assertions.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

public class BaggageTest {

    private ElasticApmTracer tracer;
    private ConfigurationRegistry config;

    private MockReporter mockReporter;

    @BeforeEach
    public void setup() {
        mockReporter = new MockReporter();
        config = SpyConfiguration.createSpyConfig();
        tracer = new ElasticApmTracerBuilder()
            .configurationRegistry(config)
            .reporter(mockReporter)
            .withObjectPoolFactory(new TestObjectPoolFactory())
            .buildAndStart();
    }

    @Test
    public void checkChildTransactionBaggageParsingWithTracestate() {
        Map<String, String> headers = new HashMap<>();
        headers.put("traceparent", "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-01");
        headers.put("baggage", "key1=val1,key2=val2");

        TransactionImpl transaction = tracer.startChildTransaction(headers, TextHeaderMapAccessor.INSTANCE, null);
        assertThat(transaction.getTraceContext())
            .satisfies(tc -> assertThat(tc.getTraceId().toString()).isEqualTo("0af7651916cd43dd8448eb211c80319c"))
            .satisfies(tc -> Assertions.assertThat(tc.getParentId().toString()).isEqualTo("b9c7c989f97918e1"));
        assertThat(transaction)
            .hasBaggageCount(2)
            .hasBaggage("key1", "val1")
            .hasBaggage("key2", "val2");
    }

    @Test
    public void checkChildTransactionBaggageParsingWithoutTracestate() {
        Map<String, String> headers = new HashMap<>();
        headers.put("baggage", "key1=val1,key2=val2");

        TransactionImpl transaction = tracer.startChildTransaction(headers, TextHeaderMapAccessor.INSTANCE, null);
        assertThat(transaction)
            .hasBaggageCount(2)
            .hasBaggage("key1", "val1")
            .hasBaggage("key2", "val2");
    }


    @Test
    public void checkChildTransactionOverridesCurrentBaggage() {
        Map<String, String> headers = new HashMap<>();
        headers.put("baggage", "key1=val1");

        TraceState<?> baggageContext = tracer.currentContext().withUpdatedBaggage()
            .put("key1", "rootval1")
            .put("key2", "rootval2")
            .buildContext()
            .activate();
        TransactionImpl transaction = tracer.startChildTransaction(headers, TextHeaderMapAccessor.INSTANCE, null);
        baggageContext.deactivate();


        assertThat(transaction)
            .hasBaggageCount(2)
            .hasBaggage("key1", "val1")
            .hasBaggage("key2", "rootval2");
    }

    @Test
    public void checkRootTransactionInheritsCurrentBaggage() {
        TraceState<?> baggageContext = tracer.currentContext().withUpdatedBaggage()
            .put("key1", "rootval1")
            .put("key2", "rootval2")
            .buildContext()
            .activate();
        TransactionImpl transaction = tracer.startRootTransaction(null);
        baggageContext.deactivate();

        assertThat(transaction)
            .hasBaggageCount(2)
            .hasBaggage("key1", "rootval1")
            .hasBaggage("key2", "rootval2");
    }


    @Test
    public void checkSpanInheritsParentBaggage() {
        TransactionImpl transaction;
        SpanImpl span1, span2, span3, span4;

        TraceState<?> baggage1 = tracer.currentContext().withUpdatedBaggage()
            .put("key1", "rootval1")
            .put("key2", "rootval2")
            .buildContext();
        try (Scope sc1 = baggage1.activateInScope()) {
            transaction = tracer.startRootTransaction(null);
            try (Scope trSc = transaction.activateInScope()) {

                span1 = tracer.currentContext().createSpan();

                TraceState<?> baggage2 = tracer.currentContext().withUpdatedBaggage()
                    .put("key1", "baggage2_override")
                    .buildContext();
                try (Scope sc2 = baggage2.activateInScope()) {

                    span2 = tracer.currentContext().createSpan();

                    try (Scope spanScope = span2.activateInScope()) {

                        span3 = tracer.currentContext().createSpan();

                        TraceState<?> baggage3 = tracer.currentContext().withUpdatedBaggage()
                            .put("key3", "baggage3_override")
                            .remove("key2")
                            .buildContext();
                        try (Scope sc3 = baggage3.activateInScope()) {
                            span4 = tracer.currentContext().createSpan();
                        }
                    }

                }
            }
        }

        assertThat(span1)
            .hasParent(transaction)
            .hasBaggageCount(2)
            .hasBaggage("key1", "rootval1")
            .hasBaggage("key2", "rootval2");

        assertThat(span2)
            .hasParent(transaction)
            .hasBaggageCount(2)
            .hasBaggage("key1", "baggage2_override")
            .hasBaggage("key2", "rootval2");

        assertThat(span3)
            .hasParent(span2)
            .hasBaggageCount(2)
            .hasBaggage("key1", "baggage2_override")
            .hasBaggage("key2", "rootval2");

        assertThat(span4)
            .hasParent(span2)
            .hasBaggageCount(2)
            .hasBaggage("key1", "baggage2_override")
            .hasBaggage("key3", "baggage3_override");
    }


    @Test
    public void checkBaggagePropagationWithoutTrace() {
        TraceState<?> baggage = tracer.currentContext().withUpdatedBaggage()
            .put("key", "val")
            .buildContext();

        Map<String, String> headers = new HashMap<>();

        assertThat(baggage.isPropagationRequired(headers, TextHeaderMapAccessor.INSTANCE)).isTrue();

        baggage.propagateContext(headers, TextHeaderMapAccessor.INSTANCE, headers, TextHeaderMapAccessor.INSTANCE);
        assertThat(headers)
            .hasSize(1)
            .containsEntry("baggage", "key=val");

        //check handling of duplicate propagation
        assertThat(baggage.isPropagationRequired(headers, TextHeaderMapAccessor.INSTANCE)).isFalse();
        baggage.propagateContext(headers, TextHeaderMapAccessor.INSTANCE, headers, TextHeaderMapAccessor.INSTANCE);
        assertThat(headers)
            .hasSize(1)
            .containsEntry("baggage", "key=val");
    }

    @Test
    public void checkBaggagePropagationFromTransaction() {
        TraceState<?> baggage = tracer.currentContext().withUpdatedBaggage()
            .put("key", "val")
            .buildContext()
            .activate();

        TransactionImpl transaction = tracer.startRootTransaction(null);

        baggage.deactivate();

        Map<String, String> headers = new HashMap<>();

        assertThat(transaction.isPropagationRequired(headers, TextHeaderMapAccessor.INSTANCE)).isTrue();

        transaction.propagateContext(headers, TextHeaderMapAccessor.INSTANCE, headers, TextHeaderMapAccessor.INSTANCE);
        assertThat(headers)
            .containsEntry("baggage", "key=val")
            .containsKey("traceparent");

    }

    @Test
    public void checkBaggagePropagationFromSpan() {
        TraceState<?> baggage = tracer.currentContext().withUpdatedBaggage()
            .put("key", "val")
            .buildContext()
            .activate();

        TransactionImpl transaction = tracer.startRootTransaction(null);
        SpanImpl span = transaction.createSpan();

        baggage.deactivate();

        Map<String, String> headers = new HashMap<>();

        assertThat(span.isPropagationRequired(headers, TextHeaderMapAccessor.INSTANCE)).isTrue();

        span.propagateContext(headers, TextHeaderMapAccessor.INSTANCE, headers, TextHeaderMapAccessor.INSTANCE);
        assertThat(headers)
            .containsEntry("baggage", "key=val")
            .containsKey("traceparent");

    }


    @Test
    public void checkBaggageLiftingToAttributes() {

        doReturn(List.of(WildcardMatcher.valueOf("foo*"), WildcardMatcher.valueOf("bar*")))
            .when(config.getConfig(CoreConfigurationImpl.class)).getBaggageToAttach();

        TraceState<?> baggage = tracer.currentContext().withUpdatedBaggage()
            .put("foo.key", "foo_val")
            .put("ignore", "ignore")
            .buildContext()
            .activate();

        TransactionImpl transaction = tracer.startRootTransaction(null);

        SpanImpl span = transaction
            .withUpdatedBaggage()
            .put("foo.key", "foo_updated_val")
            .put("bar.key", "bar_val")
            .buildContext()
            .createSpan();

        baggage.deactivate();

        assertThat(transaction.getOtelAttributes())
            .containsEntry("baggage.foo.key", "foo_val")
            .doesNotContainKey("baggage.ignore");

        assertThat(span.getOtelAttributes())
            .containsEntry("baggage.foo.key", "foo_updated_val")
            .containsEntry("baggage.bar.key", "bar_val")
            .doesNotContainKey("baggage.ignore");

    }

    @Test
    void testStandaloneExceptionCapturesBaggage() {
        doReturn(List.of(WildcardMatcher.valueOf("foo*"), WildcardMatcher.valueOf("bar*")))
            .when(config.getConfig(CoreConfigurationImpl.class)).getBaggageToAttach();

        BaggageContext parentCtx = tracer.currentContext().withUpdatedBaggage()
            .put("foo.bar", "foo_val")
            .put("ignoreme", "ignore")
            .buildContext();

        ErrorCaptureImpl errorCapture = tracer.captureException(new RuntimeException(), parentCtx, null);

        TransactionContextImpl ctx = errorCapture.getContext();
        assertThat(ctx.getLabel("baggage.foo.bar")).isEqualTo("foo_val");
        assertThat(ctx.getLabel("baggage.ignoreme")).isNull();
    }


    @Test
    void testExceptionInheritsBaggageFromParentSpan() {
        doReturn(List.of(WildcardMatcher.valueOf("foo*"), WildcardMatcher.valueOf("bar*")))
            .when(config.getConfig(CoreConfigurationImpl.class)).getBaggageToAttach();

        SpanImpl parentSpan = tracer.startRootTransaction(null)
            .withUpdatedBaggage()
            .put("foo.bar", "foo_val")
            .put("ignoreme", "ignore")
            .buildContext()
            .createSpan();

        parentSpan.captureException(new RuntimeException());

        assertThat(mockReporter.getErrors()).hasSize(1);
        ErrorCaptureImpl errorCapture = mockReporter.getErrors().get(0);

        TransactionContextImpl ctx = errorCapture.getContext();
        assertThat(ctx.getLabel("baggage.foo.bar")).isEqualTo("foo_val");
        assertThat(ctx.getLabel("baggage.ignoreme")).isNull();
    }

}
