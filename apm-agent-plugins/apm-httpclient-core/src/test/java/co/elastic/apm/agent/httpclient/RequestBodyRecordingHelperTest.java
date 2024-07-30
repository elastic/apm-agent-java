package co.elastic.apm.agent.httpclient;

import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.context.BodyCaptureImpl;
import co.elastic.apm.agent.impl.transaction.SpanImpl;
import co.elastic.apm.agent.impl.transaction.TransactionImpl;
import co.elastic.apm.agent.sdk.internal.util.IOUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class RequestBodyRecordingHelperTest {

    private ElasticApmTracer tracer;
    private MockReporter reporter;

    private TransactionImpl rootTx;

    @BeforeEach
    public void beforeEach() {
        MockTracer.MockInstrumentationSetup mockInstrumentationSetup = MockTracer.createMockInstrumentationSetup();
        tracer = mockInstrumentationSetup.getTracer();
        reporter = mockInstrumentationSetup.getReporter();
        rootTx = tracer.startRootTransaction(null);
    }

    @AfterEach
    public void afterEach() {
        rootTx.end();
        reporter.assertRecycledAfterDecrementingReferences();
        tracer.stop();
    }

    @Test
    public void ensureNoModificationAfterSpanEnd() {
        SpanImpl span = rootTx.createSpan();
        BodyCaptureImpl spanBody = span.getContext().getHttp().getRequestBody();
        spanBody.markEligibleForCapturing();
        spanBody.startCapture(null, 100);

        RequestBodyRecordingHelper helper = new RequestBodyRecordingHelper(span);
        helper.appendToBody(new byte[]{1, 2, 3, 4}, 1, 2);
        helper.appendToBody((byte) 5);

        assertThat(IOUtils.copyToByteArray(spanBody.getBody())).containsExactly(2, 3, 5);
        assertThat(helper.clientSpan).isNotNull();

        span.end();
        assertThat(helper.clientSpan).isNull();

        //Those should not and have no effect
        helper.appendToBody(new byte[]{1, 2, 3, 4}, 1, 2);
        helper.appendToBody((byte) 5);
        assertThat(IOUtils.copyToByteArray(spanBody.getBody())).containsExactly(2, 3, 5);

        RequestBodyRecordingHelper endedHelper = new RequestBodyRecordingHelper(span);
        assertThat(endedHelper.clientSpan).isNull();
    }

}
