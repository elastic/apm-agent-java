/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
 * %%
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
 * #L%
 */
package co.elastic.apm.agent.dubbo;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.dubbo.api.DubboTestApi;
import co.elastic.apm.agent.dubbo.api.exception.BizException;
import co.elastic.apm.agent.impl.context.Destination;
import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public abstract class AbstractDubboInstrumentationTest extends AbstractInstrumentationTest {

    private DubboTestApi dubboTestApi;

    static CoreConfiguration coreConfig;

    @BeforeAll
    static void initInstrumentation() {
        coreConfig = tracer.getConfig(CoreConfiguration.class);
    }

    @BeforeEach
    void startRootTransaction() {
        when(coreConfig.getCaptureBody()).thenReturn(CoreConfiguration.EventType.OFF);
        tracer.startRootTransaction(Thread.currentThread().getContextClassLoader()).withName("transaction").activate();
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
        assertThat(service.getType()).isEqualTo("external");
        assertThat(service.getResource().toString()).matches("localhost:\\d+");
        assertThat(service.getName().toString()).isEqualTo("dubbo");
    }

    abstract int getPort();
}
