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
package co.elastic.apm.agent.dubbo;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.configuration.CoreConfigurationImpl;
import co.elastic.apm.agent.dubbo.api.DubboTestApi;
import co.elastic.apm.agent.dubbo.api.exception.BizException;
import co.elastic.apm.agent.impl.error.ErrorCaptureImpl;
import co.elastic.apm.agent.impl.transaction.AbstractSpanImpl;
import co.elastic.apm.agent.impl.transaction.SpanImpl;
import co.elastic.apm.agent.impl.transaction.TransactionImpl;
import co.elastic.apm.agent.test.TestPort;
import co.elastic.apm.agent.tracer.Outcome;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import static co.elastic.apm.agent.testutils.assertions.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

public abstract class AbstractDubboInstrumentationTest extends AbstractInstrumentationTest {

    private static CoreConfigurationImpl coreConfig;

    @Nullable
    private static DubboTestApi dubboTestApi;

    private static int port = -1;

    @BeforeAll
    static void initInstrumentation() {
        coreConfig = tracer.getConfig(CoreConfigurationImpl.class);
    }

    @BeforeEach
    void beforeEach() {

        if (null == dubboTestApi) {
            // only start test dubbo once, but with delegation to subclass for creating it
            // thus we can't do that in @BeforeAll

            port = TestPort.getAvailableRandomPort();
            int backendPort = TestPort.getAvailableRandomPort();

            assertThat(port).isNotEqualTo(backendPort);
            dubboTestApi = buildDubboTestApi(port, backendPort);
        }

        doReturn(CoreConfigurationImpl.EventType.OFF).when(coreConfig).getCaptureBody();

        startTestRootTransaction("dubbo test");
    }

    @AfterEach
    void afterEach() {
        TransactionImpl transaction = tracer.currentTransaction();
        if (transaction != null) {
            transaction.deactivate().end();
        }
    }

    @AfterAll
    static void doAfterAll() {
        dubboTestApi = null;
    }

    protected static <T> T withRetry(Callable<T> task) {
        int count = 10;
        while (count > 0) {
            try {
                return task.call();
            } catch (Exception e) {
                count--;
                if (count == 0) {
                    throw new IllegalStateException("unable to start dubbo service", e);
                } else {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException ex) {
                        // silently ignored
                    }
                }
            }
        }
        throw new IllegalStateException("should not happen");
    }

    protected abstract DubboTestApi buildDubboTestApi(int port1, int port2);

    protected DubboTestApi getDubboTestApi() {
        assertThat(dubboTestApi).isNotNull();
        return dubboTestApi;
    }

    @Test
    public void testNormalReturn() {
        DubboTestApi dubboTestApi = getDubboTestApi();
        String normalReturn = dubboTestApi.normalReturn("arg1", 2);
        assertThat(normalReturn).isEqualTo("arg12");

        // transaction on the receiving side
        reporter.awaitTransactionCount(1);
        TransactionImpl transaction = reporter.getFirstTransaction();
        validateDubboTransaction(transaction, "normalReturn");

        // span on the emitting side (outgoing from this method)
        reporter.awaitSpanCount(1);
        SpanImpl span = validateDubboSpan(reporter.getFirstSpan(), "normalReturn");
        assertThat(span).isSync();

        List<ErrorCaptureImpl> errors = reporter.getErrors();
        assertThat(errors.size()).isEqualTo(0);
    }

    @Test
    public void testBizException() {
        DubboTestApi dubboTestApi = getDubboTestApi();
        try {
            dubboTestApi.throwBizException("bizException-arg1");
        } catch (Exception e) {
            // do nothing
            assertThat(e).isInstanceOf(BizException.class);
        }

        List<ErrorCaptureImpl> errors = reporter.getErrors();
        assertThat(errors).hasSize(2);
        for (ErrorCaptureImpl error : errors) {
            assertThat(error.getException()).isInstanceOf(BizException.class);
        }

        assertThat(reporter.getNumReportedSpans()).isEqualTo(1);
        assertThat(reporter.getFirstSpan()).hasOutcome(Outcome.FAILURE);
    }

    @Test
    public void testAsyncNoReturn() throws Exception {
        DubboTestApi dubboTestApi = getDubboTestApi();
        dubboTestApi.asyncNoReturn("arg1");

        assertThat(reporter.getFirstTransaction(5000)).isNotNull();
        assertThat(reporter.getFirstSpan(5000)).isNotNull();
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getSpans()).hasSize(2);

        assertThat(reporter.getSpanByName("DubboTestApi#asyncNoReturn")).isAsync();
    }

    @Test
    public void testAsyncNoReturnException() throws Exception {
        DubboTestApi dubboTestApi = getDubboTestApi();
        dubboTestApi.asyncNoReturn("error");

        assertThat(reporter.getFirstTransaction(5000)).isNotNull();
        assertThat(reporter.getFirstSpan(5000)).isNotNull();
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getSpans()).hasSize(2);
        List<ErrorCaptureImpl> errors = reporter.getErrors();
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getException()).isInstanceOf(BizException.class);

        assertThat(reporter.getSpanByName("DubboTestApi#asyncNoReturn")).isAsync();
    }

    public void validateDubboTransaction(TransactionImpl transaction, String methodName) {
        assertThat(transaction)
            .hasType("request")
            .hasName("DubboTestApi#" + methodName);
    }

    public static SpanImpl validateDubboSpan(SpanImpl span, String methodName) {
        assertThat(span)
            .hasName("DubboTestApi#" + methodName)
            .hasType("external")
            .hasSubType("dubbo");

        assertThat(span.getContext().getDestination())
            .hasLocalAddress()
            .hasPort(port);

        assertThat(span.getContext().getServiceTarget())
            .hasType("dubbo")
            .hasName(String.format("localhost:%d", port))
            .hasNameOnlyDestinationResource();

        assertThat(span.getOutcome())
            .describedAs("span outcome should be known")
            .isNotEqualTo(Outcome.UNKNOWN);

        return span;
    }

    @Test
    public void testBothProviderAndConsumer() {
        DubboTestApi dubboTestApi = getDubboTestApi();
        String arg = "hello, testBothProviderAndConsumer";
        String ret = dubboTestApi.willInvokeAnotherApi(arg);
        assertThat(ret).isEqualTo(arg);

        assertThat(reporter.getFirstTransaction(5000)).isNotNull();
        assertThat(reporter.getTransactions()).hasSize(2);
        assertThat(reporter.getSpans()).hasSize(2);

        Map<String, TransactionImpl> transactionMap = buildMap(reporter.getTransactions());
        Map<String, SpanImpl> spanMap = buildMap(reporter.getSpans());

        String testApiName = "DubboTestApi#willInvokeAnotherApi";
        String anotherApiName = "AnotherApi#echo";

        assertThat(spanMap.get(testApiName).getTraceContext().getId().toString())
            .isEqualTo(transactionMap.get(testApiName).getTraceContext().getParentId().toString());

        assertThat(transactionMap.get(testApiName).getTraceContext().getId().toString())
            .isEqualTo(spanMap.get(anotherApiName).getTraceContext().getParentId().toString());

        assertThat(spanMap.get(anotherApiName).getTraceContext().getId().toString())
            .isEqualTo(transactionMap.get(anotherApiName).getTraceContext().getParentId().toString());
    }

    public <T extends AbstractSpanImpl<?>> Map<String, T> buildMap(List<T> list) {
        Map<String, T> map = new HashMap<>();
        for (T t : list) {
            map.put(t.getNameAsString(), t);
        }
        return map;
    }
}
