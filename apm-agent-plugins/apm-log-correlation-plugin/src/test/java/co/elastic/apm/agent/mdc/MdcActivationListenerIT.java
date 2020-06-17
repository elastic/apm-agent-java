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
package co.elastic.apm.agent.mdc;

import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.error.logging.Log4j2LoggerErrorCapturingInstrumentation;
import co.elastic.apm.agent.error.logging.Slf4jLoggerErrorCapturingInstrumentation;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.ElasticApmTracerBuilder;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.logging.LoggingConfiguration;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.apache.logging.log4j.ThreadContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.MDC;
import org.stagemonitor.configuration.ConfigurationRegistry;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MdcActivationListenerIT {

    protected static ElasticApmTracer tracer;
    protected static MockReporter reporter;
    protected static ConfigurationRegistry config;
    private LoggingConfiguration loggingConfiguration;

    @BeforeAll
    static void beforeAll() {
        reporter = new MockReporter();
        config = SpyConfiguration.createSpyConfig();
        tracer = new ElasticApmTracerBuilder()
            .configurationRegistry(config)
            .reporter(reporter)
            .build();
        tracer.start();
        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install(), Arrays.asList(new Slf4jLoggerErrorCapturingInstrumentation(), new Log4j2LoggerErrorCapturingInstrumentation()));
    }

    @AfterAll
    static void afterAll() {
        ElasticApmAgent.reset();
    }

    @BeforeEach
    void setUp() throws Exception {
        MDC.clear();
        org.apache.log4j.MDC.clear();
        ThreadContext.clearAll();
        loggingConfiguration = config.getConfig(LoggingConfiguration.class);
    }

    @Test
    void testVerifyThatWithEnabledCorrelationAndLoggedErrorMdcErrorIdIsNotBlankWithSlf4j() {
        when(loggingConfiguration.isLogCorrelationEnabled()).thenReturn(true);
        Logger mockedLogger = mock(Logger.class);
        doAnswer(invocation -> assertMdcErrorIdIsNotEmpty()).when(mockedLogger).error(anyString(), any(Exception.class));

        assertMdcErrorIdIsEmpty();

        Transaction transaction = tracer.startRootTransaction(getClass().getClassLoader()).withType("request").withName("test");
        transaction.activate();
        mockedLogger.error("Some slf4j exception", new RuntimeException("Hello exception"));

        assertMdcErrorIdIsEmpty();

        transaction.deactivate().end();

        assertMdcErrorIdIsEmpty();
    }

    @Test
    void testVerifyThatWithEnabledCorrelationAndLoggedErrorMdcErrorIdIsNotBlankWithSlf4jNotInTransaction() {
        when(loggingConfiguration.isLogCorrelationEnabled()).thenReturn(true);
        Logger mockedLogger = mock(Logger.class);
        doAnswer(invocation -> assertMdcErrorIdIsNotEmpty()).when(mockedLogger).error(anyString(), any(Exception.class));

        assertMdcErrorIdIsEmpty();

        mockedLogger.error("Some slf4j exception", new RuntimeException("Hello exception"));

        assertMdcErrorIdIsEmpty();
    }

    @Test
    void testVerifyThatWithEnabledCorrelationAndLoggedErrorMdcErrorIdIsNotBlankWithLog4j() {
        Assumptions.assumeTrue(() -> {
            org.apache.log4j.MDC.put("test", true);
            return org.apache.log4j.MDC.get("test") == Boolean.TRUE;
        }, "Log4j MDC is not working, this happens with some versions of Java 10 where log4j thinks it's Java 1");
        when(loggingConfiguration.isLogCorrelationEnabled()).thenReturn(true);
        org.apache.logging.log4j.Logger logger = mock(org.apache.logging.log4j.Logger.class);
        doAnswer(invocation -> assertMdcErrorIdIsNotEmpty()).when(logger).error(anyString(), any(Exception.class));

        assertMdcErrorIdIsEmpty();

        Transaction transaction = tracer.startRootTransaction(getClass().getClassLoader()).withType("request").withName("test");
        transaction.activate();
        logger.error("Some apache logger exception", new RuntimeException("Hello exception"));
        assertMdcErrorIdIsEmpty();
        transaction.deactivate().end();

        assertMdcErrorIdIsEmpty();
    }

    @Test
    void testVerifyThatWithEnabledCorrelationAndLoggedErrorMdcErrorIdIsNotBlankWithLog4jNotInTransaction() {
        Assumptions.assumeTrue(() -> {
            org.apache.log4j.MDC.put("test", true);
            return org.apache.log4j.MDC.get("test") == Boolean.TRUE;
        }, "Log4j MDC is not working, this happens with some versions of Java 10 where log4j thinks it's Java 1");
        when(loggingConfiguration.isLogCorrelationEnabled()).thenReturn(true);
        org.apache.logging.log4j.Logger logger = mock(org.apache.logging.log4j.Logger.class);
        doAnswer(invocation -> assertMdcErrorIdIsNotEmpty()).when(logger).error(anyString(), any(Exception.class));

        assertMdcErrorIdIsEmpty();

        logger.error("Some apache logger exception", new RuntimeException("Hello exception"));

        assertMdcErrorIdIsEmpty();
    }

    private void assertMdcErrorIdIsEmpty() {
        assertThat(MDC.get("error.id")).isNull();
        assertThat(org.apache.log4j.MDC.get("error.id")).isNull();
    }

    private Answer<Void> assertMdcErrorIdIsNotEmpty() {
        assertThat(MDC.get("error.id")).isNotBlank();
        assertThat(org.apache.log4j.MDC.get("error.id")).isNotNull();
        return null;
    }
}
