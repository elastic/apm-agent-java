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
package specs;

import co.elastic.apm.agent.impl.Scope;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.OTelSpanKind;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.opentelemetry.sdk.ElasticOpenTelemetry;
import co.elastic.apm.agent.opentelemetry.sdk.ElasticOpenTelemetryTest;
import co.elastic.apm.agent.opentelemetry.sdk.OTelSpan;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

public class OTelBridgeStepsDefinitions {

    private static final String REMOTE_PARENT_TRACE_ID = "cafebabe16cd43dd8448eb211c80319c";
    private static final String REMOTE_PARENT_ID = "deadbeef197918e1";

    private final ElasticOpenTelemetry otel;

    // state will contain the Elastic state when created before OTel
    // this is required for shared steps definitions like 'an active transaction'
    private final ScenarioState state;

    private OTelSpan otelSpan;
    private boolean isOtelSpanEnded;

    private Map<String, Object> otelSpanAttributes;

    private Context localParentContext = null;

    public OTelBridgeStepsDefinitions(ScenarioState state) {
        this.state = state;
        this.otel = new ElasticOpenTelemetry(state.getTracer());
    }

    @Before
    public void resetState() {
        this.otelSpan = null;
        this.localParentContext = null;
        this.otelSpanAttributes = new HashMap<>();
        this.isOtelSpanEnded = false;
    }

    // creating elastic span or transaction from OTel span

    @Given("OTel span is created with remote context as parent")
    public void createOTelSpanWithRemoteContext() {
        otelSpan = (OTelSpan) otel.getTracer("")
            .spanBuilder("otel span")
            .setParent(getRemoteContext())
            .startSpan();

        assertThat(otelSpan.getSpanContext().getTraceId()).isEqualTo(REMOTE_PARENT_TRACE_ID);
    }

    @Then("Elastic bridged transaction has remote context as parent")
    public void bridgedTransactionWithRemoteContextParent() {
        TraceContext traceContext = getBridgedTransaction().getTraceContext();
        assertThat(traceContext.isRoot()).isFalse();
        assertThat(traceContext.getParentId().toString()).isEqualTo(REMOTE_PARENT_ID);
        assertThat(traceContext.getTraceId().toString()).isEqualTo(REMOTE_PARENT_TRACE_ID);
    }

    private Context getRemoteContext(){
        return otel.getPropagators()
            .getTextMapPropagator()
            .extract(Context.current(),
                Map.of("traceparent", String.format("00-%s-%s-01", REMOTE_PARENT_TRACE_ID, REMOTE_PARENT_ID),
                    "tracestate", "k=v"),
                new ElasticOpenTelemetryTest.MapGetter());
    }

    @Given("OTel span is created without parent")
    public void createOTelSpanWithoutParent(){
        otelSpan = (OTelSpan) otel.getTracer("")
            .spanBuilder("otel span")
            .setNoParent() // redundant, but makes it explicit
            .startSpan();
    }

    @Then("Elastic bridged transaction is a root transaction")
    public void bridgedTransactionIsRootTransaction() {
        TraceContext traceContext = getBridgedTransaction().getTraceContext();
        assertThat(traceContext.isRoot()).isTrue();
    }

    @Given("OTel span is created with local context as parent")
    public void createOTelSpanWithLocalParent() {
        localParentContext = Context.root().with(otel.getTracer("").spanBuilder("parent").startSpan());

        otelSpan = (OTelSpan) otel.getTracer("")
            .spanBuilder("otel span")
            .setParent(localParentContext)
            .startSpan();

    }

    @Then("Elastic bridged span has local context as parent")
    public void bridgedSpanHasLocalParent() {
        assertThat(localParentContext).isNotNull();

        SpanContext otelParentContext = io.opentelemetry.api.trace.Span.fromContext(localParentContext).getSpanContext();

        TraceContext bridgedSpanContext = getBridgedSpan().getTraceContext();
        assertThat(bridgedSpanContext.getTraceId().toString()).isEqualTo(otelParentContext.getTraceId());
        assertThat(bridgedSpanContext.getParentId().toString()).isEqualTo(otelParentContext.getSpanId());
    }

    // OTel span kind mapping for spans & transactions

    @Given("OTel span is created with kind {string}")
    public void otelSpanIsCreatedWithKind(String kind) {
        // we have to use a parent transaction as we are creating a span
        // the parent transaction is created by another step definition, thus we reuse the existing state
        Transaction parentTransaction = state.getTransaction();

        Function<String,OTelSpan> createSpanWithKind = k -> {
            SpanBuilder spanBuilder = otel.getTracer("")
                .spanBuilder("span")
                .setSpanKind(SpanKind.valueOf(k));
            return (OTelSpan) spanBuilder.startSpan();
        };

        if( parentTransaction != null){
            // creating a span as a child of existing transaction
            try (Scope scope = parentTransaction.activateInScope()) {
                this.otelSpan = createSpanWithKind.apply(kind);
            }
        } else {
            // creating a root transaction
            this.otelSpan = createSpanWithKind.apply(kind);
        }

    }

    @Given("OTel span has following attributes")
    public void otelSpanAttributes(io.cucumber.datatable.DataTable table) {
        table.cells().forEach(r -> {
            String key = r.get(0);
            String stringValue = r.get(1);
            if (stringValue != null) {
                AttributeKey attributeKey = lookupKey(key);

                Object valueAsObject;
                switch (attributeKey.getType()) {
                    case LONG:
                        Long longValue = Long.parseLong(stringValue);
                        otelSpan.setAttribute(attributeKey, longValue);
                        valueAsObject = longValue;
                        break;
                    case BOOLEAN:
                        Boolean booleanValue = Boolean.parseBoolean(stringValue);
                        otelSpan.setAttribute(attributeKey, booleanValue);
                        valueAsObject = booleanValue;
                        break;
                    default:
                        otelSpan.setAttribute(key, stringValue);
                        valueAsObject = stringValue;
                }
                otelSpanAttributes.put(key, valueAsObject);
            }
        });
    }

    private static AttributeKey<?> lookupKey(String name) {
        switch (name) {
            case "http.url":
                return SemanticAttributes.HTTP_URL;
            case "http.scheme":
                return SemanticAttributes.HTTP_SCHEME;
            case "http.host":
                return SemanticAttributes.HTTP_HOST;
            case "net.peer.name":
                return SemanticAttributes.NET_PEER_NAME;
            case "net.peer.ip":
                return SemanticAttributes.NET_PEER_IP;
            case "net.peer.port":
                return SemanticAttributes.NET_PEER_PORT;
            case "db.system":
                return SemanticAttributes.DB_SYSTEM;
            case "db.name":
                return SemanticAttributes.DB_NAME;
            case "messaging.system":
                return SemanticAttributes.MESSAGING_SYSTEM;
            case "messaging.url":
                return SemanticAttributes.MESSAGING_URL;
            case "messaging.destination":
                return SemanticAttributes.MESSAGING_DESTINATION;
            case "rpc.system":
                return SemanticAttributes.RPC_SYSTEM;
            case "rpc.service":
                return SemanticAttributes.RPC_SERVICE;
            default:
                throw new IllegalArgumentException("unknown key for name " + name);
        }
    }

    @Then("Elastic bridged (transaction|span) OTel kind is {string}")
    public void bridgeObjectKind(String kind){
        assertThat(getBridgedAbstractSpan().getOtelKind())
            .isEqualTo(OTelSpanKind.valueOf(kind));
    }

    @Then("Elastic bridged object is a span")
    public void bridgeObjectTypeSpan() {
        getBridgedSpan();
    }

    @Then("Elastic bridged object is a transaction")
    public void bridgeObjectTypeTransaction() {
        getBridgedTransaction();
    }

    @Then("Elastic bridged (transaction|span) type is {string}")
    public void bridgeObjectType(String expected) {
        AbstractSpan<?> bridgedObject = getBridgedAbstractSpan();
        String type;
        if (bridgedObject instanceof Transaction) {
            type = ((Transaction) bridgedObject).getType();
        } else {
            type = ((Span) bridgedObject).getType();
        }

        assertThat(type).isEqualTo(expected);
    }

    @Then("Elastic bridged span subtype is {string}")
    public void bridgeObjectSubtype(String expected) {
        if (expected.isEmpty()) {
            expected = null;
        }
        assertThat(getBridgedSpan().getSubtype()).isEqualTo(expected);
    }

    @Then("Elastic bridged span OTel attributes are copied as-is")
    public void bridgeObjectOTelAttributesCheck() {
        assertThat(otelSpan.getInternalSpan().getOtelAttributes())
            .containsExactlyEntriesOf(otelSpanAttributes);
    }

    @Then("Elastic bridged span destination resource is not set")
    public void bridgeObjectDestinationResourceNotSet() {
        assertThat(getDestinationResource()).isEmpty();
    }

    @Then("Elastic bridged span destination resource is set to {string}")
    public void bridgeObjectDestinationResource(String expected) {
        assertThat(getDestinationResource())
            .describedAs("destination resource expected for otel attributes: %s", getBridgedSpan().getOtelAttributes())
            .isEqualTo(expected);
    }

    private String getDestinationResource() {
        return getBridgedSpan().getContext().getDestination().getService().getResource().toString();
    }

    private AbstractSpan<?> getBridgedAbstractSpan() {
        return getBridgedObject(AbstractSpan.class);
    }

    private Transaction getBridgedTransaction() {
        return getBridgedObject(Transaction.class);
    }

    private Span getBridgedSpan() {
        return getBridgedObject(Span.class);
    }

    private <T extends AbstractSpan<?>> T getBridgedObject(Class<T> expectedType) {
        // span should be ended lazily on first access as part of the mapping is performed when ending the span
        if (!isOtelSpanEnded) {
            otelSpan.end();
            isOtelSpanEnded = true;
        }

        AbstractSpan<?> internalSpan = otelSpan.getInternalSpan();
        assertThat(internalSpan).isInstanceOf(expectedType);
        return expectedType.cast(internalSpan);
    }

}
