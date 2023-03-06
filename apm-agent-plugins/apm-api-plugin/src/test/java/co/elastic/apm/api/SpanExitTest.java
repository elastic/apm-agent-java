package co.elastic.apm.api;

import co.elastic.apm.AbstractApiTest;
import co.elastic.apm.agent.impl.transaction.Span;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static co.elastic.apm.agent.testutils.assertions.Assertions.assertThat;


public class SpanExitTest extends AbstractApiTest {

    @BeforeEach
    void before(){
        // user-provided spans are not expected to fit the general requirements for exit spans
        reporter.disableCheckStrictSpanType();
        reporter.disableCheckDestinationAddress();
    }

    @Test
    void annotationExitSpan1() {
        Span span = scenario(() -> captureExitSpan1());
        assertThat(span)
            .hasName("SpanExitTest#captureExitSpan1")
            .hasType("app")
            .isExit();
    }

    @Test
    void annotationNonExitSpanByDefault() {
        Span span = scenario(() -> captureSpan());
        assertThat(span)
            .hasName("SpanExitTest#captureSpan")
            .hasType("app")
            .isNotExit();
    }

    @Test
    void annotationExitSpan2() {
        Span span = scenario(() -> captureExitSpan2());
        assertThat(span)
            .hasName("SpanExitTest#captureExitSpan2")
            .hasType("app")
            .hasSubType("my-database")
            .isExit();
    }

    private Span scenario(Runnable task){
        Transaction transaction = ElasticApm.startTransaction();
        try (Scope activate = transaction.activate()) {
            task.run();
        }
        transaction.end();
        List<Span> spans = reporter.getSpans();
        assertThat(spans).hasSize(1);
        return spans.get(0);
    }

    @CaptureSpan
    void captureSpan(){

    }

    @CaptureSpan(exit = true)
    void captureExitSpan1(){

    }

    @CaptureSpan(exit = true, subtype = "my-database")
    void captureExitSpan2(){

    }

}
