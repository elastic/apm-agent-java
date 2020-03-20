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
import co.elastic.apm.agent.impl.context.TransactionContext;
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

    private static CoreConfiguration coreConfig;

    @BeforeAll
    static void initInstrumentation() {
        coreConfig = tracer.getConfig(CoreConfiguration.class);
    }

    @BeforeEach
    void startRootTransaction() {
        when(coreConfig.getCaptureBody()).thenReturn(CoreConfiguration.EventType.OFF);
        tracer.startRootTransaction(Thread.currentThread().getContextClassLoader()).activate();
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
        validateDubboTransaction(
            transactions.get(0), DubboTestApi.class,
            "normalReturn", new Class<?>[]{String.class, Integer.class});
        noCaptureBody(transactions.get(0));


        List<Span> spans = reporter.getSpans();
        assertThat(spans.size()).isEqualTo(1);
        validateDubboSpan(spans.get(0), DubboTestApi.class,
            "normalReturn", new Class<?>[]{String.class, Integer.class});

        List<ErrorCapture> errors = reporter.getErrors();
        assertThat(errors.size()).isEqualTo(0);
    }

    @Test
    public void testUnexpectedException() {
        DubboTestApi dubboTestApi = getDubboTestApi();
        try {
            dubboTestApi.throwUnexpectedException("unexpectedException-arg1");
        } catch (Exception e) {
            // do nothing
        }

        List<Transaction> transactions = reporter.getTransactions();
        assertThat(transactions.size()).isEqualTo(1);
        Transaction transaction = transactions.get(0);
        noCaptureBody(transaction);

        List<ErrorCapture> errors = reporter.getErrors();
        assertThat(errors.size()).isEqualTo(1);
        ErrorCapture error = errors.get(0);
        assertThat(error.getTraceContext().getParentId()).isEqualTo(transaction.getTraceContext().getId());
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

        noCaptureBody(reporter.getTransactions().get(0));
        List<ErrorCapture> errors = reporter.getErrors();
        assertThat(errors.size()).isEqualTo(0);
    }

    @Test
    public void testNormalReturnAndCaptureError() {
        when(coreConfig.getCaptureBody()).thenReturn(CoreConfiguration.EventType.ERRORS);
        DubboTestApi dubboTestApi = getDubboTestApi();
        dubboTestApi.normalReturn("arg1", 2);
        List<Transaction> transactions = reporter.getTransactions();
        assertThat(transactions.size()).isEqualTo(1);
        noCaptureBody(transactions.get(0));
    }

    @Test
    public void testNormalReturnAndCaptureTransaction() {
        when(coreConfig.getCaptureBody()).thenReturn(CoreConfiguration.EventType.ALL);
        DubboTestApi dubboTestApi = getDubboTestApi();
        String retValue = dubboTestApi.normalReturn("arg1", 2);
        List<Transaction> transactions = reporter.getTransactions();
        assertThat(transactions.size()).isEqualTo(1);
        validateNormalReturnCapture(transactions.get(0), new Object[]{"arg1", 2}, retValue);
    }

    @Test
    public void testBizExceptionAndCaptureError() {
        String arg = "bizException";
        when(coreConfig.getCaptureBody()).thenReturn(CoreConfiguration.EventType.ERRORS);
        DubboTestApi dubboTestApi = getDubboTestApi();
        try {
            dubboTestApi.throwBizException(arg);
        } catch (Exception e) {
            List<Transaction> transactions = reporter.getTransactions();
            assertThat(transactions.size()).isEqualTo(1);
            validateBizExceptionCapture(transactions.get(0), new Object[]{arg}, e);
            return;
        }
        throw new RuntimeException("not ok");
    }

    @Test
    public void testBizExceptionAndCaptureTransaction() {
        String arg = "bizException";
        when(coreConfig.getCaptureBody()).thenReturn(CoreConfiguration.EventType.TRANSACTIONS);
        DubboTestApi dubboTestApi = getDubboTestApi();
        try {
            dubboTestApi.throwBizException(arg);
        } catch (Exception e) {
            List<Transaction> transactions = reporter.getTransactions();
            assertThat(transactions.size()).isEqualTo(1);
            validateBizExceptionCapture(transactions.get(0), new Object[]{arg}, e);
            return;
        }
        throw new RuntimeException("not ok");
    }

    @Test
    public void testUnexpectedExceptionAndCaptureError() {
        String arg = "unexpectedException";
        when(coreConfig.getCaptureBody()).thenReturn(CoreConfiguration.EventType.ERRORS);
        DubboTestApi dubboTestApi = getDubboTestApi();
        try {
            dubboTestApi.throwUnexpectedException(arg);
        } catch (Exception e) {
            List<Transaction> transactions = reporter.getTransactions();
            assertThat(transactions.size()).isEqualTo(1);
            validateUnexpectedExceptionCapture(transactions.get(0), new Object[]{arg}, e);
            return;
        }
        throw new RuntimeException("not ok");
    }

    @Test
    public void testUnexpectedExceptionAndCaptureTransaction() {
        String arg = "unexpectedException";
        when(coreConfig.getCaptureBody()).thenReturn(CoreConfiguration.EventType.TRANSACTIONS);
        DubboTestApi dubboTestApi = getDubboTestApi();
        try {
            dubboTestApi.throwUnexpectedException(arg);
        } catch (Exception e) {
            List<Transaction> transactions = reporter.getTransactions();
            assertThat(transactions.size()).isEqualTo(1);
            validateUnexpectedExceptionCapture(transactions.get(0), new Object[]{arg}, e);
            return;
        }
        throw new RuntimeException("not ok");
    }

    @Test
    public void testTimeout() {
        try {
            dubboTestApi.timeout("hello");
        } catch (Exception e) {

        }
    }

    private void noCaptureBody(Transaction transaction) {
        assertThat(transaction.getContext().hasCustom()).isFalse();
    }

    private void validateNormalReturnCapture(Transaction transaction, Object[] args, Object returnValue) {
        TransactionContext context = transaction.getContext();
        validateArgs(args, context);
        assertThat(context.getCustom("return")).isEqualTo(returnValue != null ? returnValue.toString() : "null");
    }

    private void validateBizExceptionCapture(Transaction transaction, Object[] args, Throwable t) {
        TransactionContext context = transaction.getContext();
        validateArgs(args, context);
        assertThat(context.getCustom("throw")).isEqualTo(t.toString());
    }

    private void validateUnexpectedExceptionCapture(Transaction transaction, Object[] args, Throwable t) {
        validateBizExceptionCapture(transaction, args, t);
    }

    private void validateArgs(Object[] args, TransactionContext context) {
        assertThat(context.hasCustom()).isTrue();
        if (args != null && args.length > 0) {
            for (int i = 0; i < args.length; i++) {
                assertThat(context.getCustom("arg-" + i)).isEqualTo(args[i].toString());
            }
        }
    }

    public void validateDubboTransaction(Transaction transaction,
                                         Class<?> apiClass,
                                         String methodName,
                                         Class<?>[] paramClasses) {
        assertThat(transaction.getNameAsString()).isEqualTo(getDubboName(apiClass, methodName, paramClasses));
        assertThat(transaction.getType()).isEqualTo("dubbo");
    }

    protected String getDubboName(Class<?> apiClass,
                                  String methodName,
                                  Class<?>[] paramClasses) {
        String paramSign = "()";
        if (paramClasses != null && paramClasses.length > 0) {
            StringBuilder sb = new StringBuilder("(" + paramClasses[0].getSimpleName());
            for (int i = 1; i < paramClasses.length; i++) {
                sb.append(",").append(paramClasses[i].getSimpleName());
            }
            sb.append(")");
            paramSign = sb.toString();
        }
        return apiClass.getName() + "." + methodName + paramSign;
    }

    public void validateDubboSpan(Span span,
                                  Class<?> apiClass,
                                  String methodName,
                                  Class<?>[] paramClasses) {
        assertThat(span.getNameAsString()).isEqualTo(getDubboName(apiClass, methodName, paramClasses));
        assertThat(span.getType()).isEqualTo("external");
        assertThat(span.getSubtype()).isEqualTo("dubbo");
        Destination destination = span.getContext().getDestination();
        assertThat(destination.getAddress().toString()).isEqualTo("localhost");
        assertThat(destination.getPort()).isEqualTo(getPort());

        Destination.Service service = destination.getService();
        assertThat(service.getType()).isEqualTo("external");
        assertThat(service.getResource().toString()).isEqualTo("dubbo");
        assertThat(service.getName().toString()).isEqualTo("dubbo");
    }

    abstract int getPort();
}
