package co.elastic.apm.agent.opentelemetry.sdk;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Transaction;
import com.fasterxml.jackson.databind.JsonNode;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import specs.TestJsonSpec;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.Locale;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

public class OTelInferenceTest extends AbstractInstrumentationTest {

    private OpenTelemetry otel;
    private Tracer otelTracer;

    @BeforeEach
    void setUp() {
        this.otel = GlobalOpenTelemetry.get();
        assertThat(otel).isSameAs(GlobalOpenTelemetry.get());
        otelTracer = otel.getTracer(null);

        // otel spans are not recycled for now
        disableRecyclingValidation();
    }

    @ParameterizedTest
    @MethodSource("getTestCases")
    void runTest(JsonNode testCase) {

        String description = textAttribute(testCase, "description_message");
        boolean createSpan = textAttribute(testCase, "object_to_create").equals("span");

        JsonNode jsonOtelSpan = testCase.get("otel_span");

        SpanKind kind = SpanKind.valueOf(textAttribute(jsonOtelSpan, "kind").toUpperCase(Locale.ROOT));

        JsonNode jsonAttributes = jsonOtelSpan.get("attributes");

        String expectedType = textAttribute(testCase, "expected_type");
        String expectedSubType = optionalTextAttribute(testCase, "expected_subtype");
        String expectedResource = optionalTextAttribute(testCase, "expected_resource");

        if (!createSpan) {
            assertThat(expectedSubType)
                .describedAs("subtype should not be set for transactions")
                .isNull();
            assertThat(expectedResource)
                .describedAs("resource should not be set for transactions")
                .isNull();
        }

        AbstractSpan<?> elasticSpan;

        if (createSpan) {
            testSpan(description, kind, jsonAttributes);

            assertThat(reporter.getNumReportedSpans()).isEqualTo(1);
            assertThat(reporter.getNumReportedTransactions()).isEqualTo(1);

            elasticSpan = reporter.getFirstSpan();

            assertThat(elasticSpan).isInstanceOf(co.elastic.apm.agent.impl.transaction.Span.class);
            co.elastic.apm.agent.impl.transaction.Span span = (co.elastic.apm.agent.impl.transaction.Span) elasticSpan;

            assertThat(span.getType())
                .isEqualTo(expectedType);

            assertThat(span.getSubtype())
                .isEqualTo(expectedSubType);

            if(expectedResource != null){
                assertThat(span.getContext().getDestination().getService().getResource().toString())
                    .isEqualTo(expectedResource);
            }

        } else {
            testTransaction(description, kind, jsonAttributes);

            assertThat(reporter.getNumReportedSpans()).isEqualTo(0);
            assertThat(reporter.getNumReportedTransactions()).isEqualTo(1);

            elasticSpan = reporter.getFirstTransaction();

            assertThat(elasticSpan).isNotNull();

            assertThat(elasticSpan).isInstanceOf(Transaction.class);
            Transaction transaction = (Transaction) elasticSpan;



            assertThat(transaction.getType())
                .isEqualTo(expectedType);
        }

    }

    private Span testTransaction(String description, SpanKind kind, JsonNode attributes) {
        Span span = startOtelSpan(String.format("transaction %s", description), kind);
        applyOtelAttributes(span, attributes);
        span.end();
        return span;
    }

    private Span testSpan(String description, SpanKind kind, JsonNode attributes) {
        Span transactionSpan = startOtelSpan(String.format("parent transaction for %s", description), SpanKind.SERVER);
        Span spanSpan = startOtelSpan(String.format("span %s", description), kind);
        applyOtelAttributes(spanSpan, attributes);
        spanSpan.end();
        transactionSpan.end();
        return spanSpan;
    }

    private Span startOtelSpan(String name, SpanKind kind) {
        return otelTracer.spanBuilder(name)
            .setSpanKind(kind)
            .startSpan();
    }

    private void applyOtelAttributes(Span span, JsonNode attributes) {
        attributes.fields()
            .forEachRemaining(e -> {
                JsonNode jsonValue = e.getValue();
                if (jsonValue.isBoolean()) {
                    span.setAttribute(e.getKey(), jsonValue.asBoolean());
                } else if (jsonValue.isNumber()) {
                    span.setAttribute(e.getKey(), jsonValue.asLong());
                } else if (jsonValue.isTextual()) {
                    span.setAttribute(e.getKey(), jsonValue.asText());
                } else {
                    throw new IllegalStateException();
                }
            });
    }

    private static String textAttribute(JsonNode json, String name) {
        return Optional.ofNullable(json.get(name))
            .map(JsonNode::asText)
            .orElseThrow(()-> new IllegalStateException("missing mandatory attribute " + name));
    }

    @Nullable
    private static String optionalTextAttribute(JsonNode json, String name) {
        return Optional.ofNullable(json.get(name))
            .map(v -> v.isNull() ? null : v.asText())
            .orElse(null);
    }

    private static Stream<Named<JsonNode>> getTestCases() {
        Iterator<JsonNode> iterator = TestJsonSpec.getJson("otel_bridge_inference.json").iterator();
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED), false)
            .map(test -> {
                String description = test.get("description_message").asText();
                assertThat(description).isNotNull().isNotEmpty();
                return Named.of(description, test);
            });
    }
}
