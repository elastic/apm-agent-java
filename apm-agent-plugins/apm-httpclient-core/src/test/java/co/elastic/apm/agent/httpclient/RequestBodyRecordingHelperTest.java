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
        spanBody.markPreconditionsPassed(null, 100);
        spanBody.startCapture();

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

    @Test
    public void ensureLimitRespected() {
        SpanImpl span = rootTx.createSpan();
        BodyCaptureImpl spanBody = span.getContext().getHttp().getRequestBody();
        spanBody.markEligibleForCapturing();
        spanBody.markPreconditionsPassed(null, 3);
        spanBody.startCapture();

        RequestBodyRecordingHelper helper = new RequestBodyRecordingHelper(span);
        helper.appendToBody((byte) 1);
        helper.appendToBody(new byte[]{2, 3, 4, 5}, 1, 2);
        helper.appendToBody((byte) 6);

        assertThat(IOUtils.copyToByteArray(spanBody.getBody())).containsExactly(1, 3, 4);
        assertThat(helper.clientSpan).isNull();

        span.end();
    }

}
