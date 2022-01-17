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
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.dubbo.api.DubboTestApi;
import co.elastic.apm.agent.dubbo.api.exception.BizException;
import co.elastic.apm.agent.impl.context.Destination;
import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Outcome;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.testutils.TestPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public abstract class AbstractDubboInstrumentationTest extends AbstractInstrumentationTest {

    private final int port = TestPort.getAvailableRandomPort();
    private final int anotherPort = TestPort.getAvailableRandomPort();

    @Nullable
    private DubboTestApi dubboTestApi;

    static CoreConfiguration coreConfig;

    @BeforeAll
    static void initInstrumentation() {
        coreConfig = tracer.getConfig(CoreConfiguration.class);
    }

    @BeforeEach
    void startRootTransaction() {
        when(coreConfig.getCaptureBody()).thenReturn(CoreConfiguration.EventType.OFF);

        // using context classloader is required here
        ClassLoader cl = Thread.currentThread().getContextClassLoader();

        Transaction transaction = tracer.startRootTransaction(cl);
        assertThat(transaction).isNotNull();
        transaction
            .withName("dubbo test")
            .withType("test")
            .withResult("success")
            .withOutcome(Outcome.SUCCESS)
            .activate();

    }

    @AfterEach
    void clearReporter() {
        tracer.currentTransaction().deactivate().end();
    }

    protected abstract DubboTestApi buildDubboTestApi();

    public DubboTestApi getDubboTestApi() {
        if (dubboTestApi == null) {
            dubboTestApi = buildDubboTestApi();
        }
        return dubboTestApi;
    }

    @Test
    public void testNormalReturn() {
        DubboTestApi dubboTestApi = getDubboTestApi();
        String normalReturn = dubboTestApi.normalReturn("arg1", 2);
        assertThat(normalReturn).isEqualTo("arg12");
        List<Transaction> transactions = reporter.getTransactions();
        assertThat(transactions.size()).isEqualTo(1);
        validateDubboTransaction(transactions.get(0), DubboTestApi.class, "normalReturn");

        List<Span> spans = reporter.getSpans();
        assertThat(spans.size()).isEqualTo(1);
        validateDubboSpan(spans.get(0), DubboTestApi.class, "normalReturn");

        List<ErrorCapture> errors = reporter.getErrors();
        assertThat(errors.size()).isEqualTo(0);
    }

    @Test
    public void testBizException() {
        DubboTestApi dubboTestApi = getDubboTestApi();
        try {
            dubboTestApi.throwBizException("bizException-arg1");
        } catch (Exception e) {
            // do nothing
            assertThat(e instanceof BizException).isTrue();
        }

        List<ErrorCapture> errors = reporter.getErrors();
        assertThat(errors.size()).isEqualTo(2);
        for (ErrorCapture error : errors) {
            Throwable t = error.getException();
            assertThat(t instanceof BizException).isTrue();
        }

        assertThat(reporter.getNumReportedSpans()).isEqualTo(1);
        Span span = reporter.getFirstSpan();
        assertThat(span.getOutcome()).isEqualTo(Outcome.FAILURE);
    }

    @Test
    public void testAsyncNoReturn() throws Exception {
        DubboTestApi dubboTestApi = getDubboTestApi();
        dubboTestApi.asyncNoReturn("arg1");

        assertThat(reporter.getFirstTransaction(5000)).isNotNull();
        assertThat(reporter.getFirstSpan(5000)).isNotNull();
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getSpans()).hasSize(2);
    }

    @Test
    public void testAsyncNoReturnException() throws Exception {
        DubboTestApi dubboTestApi = getDubboTestApi();
        dubboTestApi.asyncNoReturn("error");

        assertThat(reporter.getFirstTransaction(5000)).isNotNull();
        assertThat(reporter.getFirstSpan(5000)).isNotNull();
        assertThat(reporter.getTransactions()).hasSize(1);
        assertThat(reporter.getSpans()).hasSize(2);
        List<ErrorCapture> errors = reporter.getErrors();
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getException() instanceof BizException).isTrue();
    }

    public void validateDubboTransaction(Transaction transaction, Class<?> apiClass, String methodName) {
        assertThat(transaction.getNameAsString()).isEqualTo(getDubboName(apiClass, methodName));
        assertThat(transaction.getType()).isEqualTo("request");
    }

    protected String getDubboName(Class<?> apiClass, String methodName) {
        return apiClass.getSimpleName() + "#" + methodName;
    }

    public void validateDubboSpan(Span span, Class<?> apiClass, String methodName) {
        assertThat(span.getNameAsString()).isEqualTo(getDubboName(apiClass, methodName));
        assertThat(span.getType()).isEqualTo("external");
        assertThat(span.getSubtype()).isEqualTo("dubbo");
        Destination destination = span.getContext().getDestination();
        assertThat(destination.getAddress().toString()).isEqualTo("localhost");
        assertThat(destination.getPort()).isEqualTo(getPort());

        Destination.Service service = destination.getService();
        assertThat(service.getResource().toString()).matches("localhost:\\d+");

        assertThat(span.getOutcome())
            .describedAs("span outcome should be known")
            .isNotEqualTo(Outcome.UNKNOWN);
    }

    protected int getPort() {
        return port;
    }

    protected int getAnotherApiPort() {
        return anotherPort;
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

        Map<String, Transaction> transactionMap = buildMap(reporter.getTransactions());
        Map<String, Span> spanMap = buildMap(reporter.getSpans());

        String testApiName = "DubboTestApi#willInvokeAnotherApi";
        String anotherApiName = "AnotherApi#echo";

        assertThat(spanMap.get(testApiName).getTraceContext().getId().toString())
            .isEqualTo(transactionMap.get(testApiName).getTraceContext().getParentId().toString());

        assertThat(transactionMap.get(testApiName).getTraceContext().getId().toString())
            .isEqualTo(spanMap.get(anotherApiName).getTraceContext().getParentId().toString());

        assertThat(spanMap.get(anotherApiName).getTraceContext().getId().toString())
            .isEqualTo(transactionMap.get(anotherApiName).getTraceContext().getParentId().toString());
    }

    public <T extends AbstractSpan<?>> Map<String, T> buildMap(List<T> list) {
        Map<String, T> map = new HashMap<>();
        for (T t : list) {
            map.put(t.getNameAsString(), t);
        }
        return map;
    }
}
