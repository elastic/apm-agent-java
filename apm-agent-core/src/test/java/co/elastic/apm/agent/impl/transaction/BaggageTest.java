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
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.ElasticApmTracerBuilder;
import co.elastic.apm.agent.impl.TextHeaderMapAccessor;
import co.elastic.apm.agent.objectpool.TestObjectPoolFactory;
import co.elastic.apm.agent.tracer.ElasticContext;
import co.elastic.apm.agent.tracer.Scope;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.stagemonitor.configuration.ConfigurationRegistry;

import java.util.HashMap;
import java.util.Map;

import static co.elastic.apm.agent.testutils.assertions.Assertions.assertThat;

public class BaggageTest {

    private ElasticApmTracer tracer;
    private ConfigurationRegistry config;

    @BeforeEach
    public void setup() {
        config = SpyConfiguration.createSpyConfig();
        tracer = new ElasticApmTracerBuilder()
            .configurationRegistry(config)
            .reporter(new MockReporter())
            .withObjectPoolFactory(new TestObjectPoolFactory())
            .buildAndStart();
    }

    @Test
    public void checkChildTransactionBaggageParsingWithTracestate() {
        Map<String, String> headers = new HashMap<>();
        headers.put("traceparent", "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-01");
        headers.put("baggage", "key1=val1,key2=val2");

        Transaction transaction = tracer.startChildTransaction(headers, TextHeaderMapAccessor.INSTANCE, null);
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

        Transaction transaction = tracer.startChildTransaction(headers, TextHeaderMapAccessor.INSTANCE, null);
        assertThat(transaction)
            .hasBaggageCount(2)
            .hasBaggage("key1", "val1")
            .hasBaggage("key2", "val2");
    }


    @Test
    public void checkChildTransactionOverridesCurrentBaggage() {
        Map<String, String> headers = new HashMap<>();
        headers.put("baggage", "key1=val1");

        ElasticContext<?> baggageContext = tracer.currentContext().withUpdatedBaggage()
            .put("key1", "rootval1")
            .put("key2", "rootval2")
            .buildContext()
            .activate();
        Transaction transaction = tracer.startChildTransaction(headers, TextHeaderMapAccessor.INSTANCE, null);
        baggageContext.deactivate();


        assertThat(transaction)
            .hasBaggageCount(2)
            .hasBaggage("key1", "val1")
            .hasBaggage("key2", "rootval2");
    }

    @Test
    public void checkRootTransactionInheritsCurrentBaggage() {
        ElasticContext<?> baggageContext = tracer.currentContext().withUpdatedBaggage()
            .put("key1", "rootval1")
            .put("key2", "rootval2")
            .buildContext()
            .activate();
        Transaction transaction = tracer.startRootTransaction(null);
        baggageContext.deactivate();

        assertThat(transaction)
            .hasBaggageCount(2)
            .hasBaggage("key1", "rootval1")
            .hasBaggage("key2", "rootval2");
    }


    @Test
    public void checkSpanInheritsParentBaggage() {
        Transaction transaction;
        Span span1, span2, span3, span4;

        ElasticContext<?> baggage1 = tracer.currentContext().withUpdatedBaggage()
            .put("key1", "rootval1")
            .put("key2", "rootval2")
            .buildContext();
        try (Scope sc1 = baggage1.activateInScope()) {
            transaction = tracer.startRootTransaction(null);
            try (Scope trSc = transaction.activateInScope()) {

                span1 = tracer.currentContext().createSpan();

                ElasticContext<?> baggage2 = tracer.currentContext().withUpdatedBaggage()
                    .put("key1", "baggage2_override")
                    .buildContext();
                try (Scope sc2 = baggage2.activateInScope()) {

                    span2 = tracer.currentContext().createSpan();

                    try (Scope spanScope = span2.activateInScope()) {

                        span3 = tracer.currentContext().createSpan();

                        ElasticContext<?> baggage3 = tracer.currentContext().withUpdatedBaggage()
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
        ElasticContext<?> baggage = tracer.currentContext().withUpdatedBaggage()
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
        ElasticContext<?> baggage = tracer.currentContext().withUpdatedBaggage()
            .put("key", "val")
            .buildContext()
            .activate();

        Transaction transaction = tracer.startRootTransaction(null);

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
        ElasticContext<?> baggage = tracer.currentContext().withUpdatedBaggage()
            .put("key", "val")
            .buildContext()
            .activate();

        Transaction transaction = tracer.startRootTransaction(null);
        Span span = transaction.createSpan();

        baggage.deactivate();

        Map<String, String> headers = new HashMap<>();

        assertThat(span.isPropagationRequired(headers, TextHeaderMapAccessor.INSTANCE)).isTrue();

        span.propagateContext(headers, TextHeaderMapAccessor.INSTANCE, headers, TextHeaderMapAccessor.INSTANCE);
        assertThat(headers)
            .containsEntry("baggage", "key=val")
            .containsKey("traceparent");

    }

}
