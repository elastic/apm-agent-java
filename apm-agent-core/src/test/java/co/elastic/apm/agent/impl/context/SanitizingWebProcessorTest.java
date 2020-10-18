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
package co.elastic.apm.agent.impl.context;

import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static co.elastic.apm.agent.impl.context.AbstractContext.REDACTED_CONTEXT_STRING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SanitizingWebProcessorTest {

    private SanitizingWebProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new SanitizingWebProcessor(SpyConfiguration.createSpyConfig());
    }

    @Test
    void processTransactions() {
        Transaction transaction = new Transaction(MockTracer.create());
        fillContext(transaction.getContext());

        processor.processBeforeReport(transaction);

        assertContainsNoSensitiveInformation(transaction.getContext());
    }

    @Test
    void processErrors() {
        final ErrorCapture errorCapture = new ErrorCapture(MockTracer.create());
        fillContext(errorCapture.getContext());

        processor.processBeforeReport(errorCapture);

        assertContainsNoSensitiveInformation(errorCapture.getContext());
    }

    private void fillContext(TransactionContext context) {
        context.getRequest().addCookie("JESESSIONID", "CAFEBABE");
        context.getRequest().addCookie("non-sensitive", "foo");
        context.getRequest().addFormUrlEncodedParameter("cretidCard", "1234 1234 1234 1234");
        context.getRequest().addFormUrlEncodedParameter("non-sensitive", "foo");
        context.getRequest().addHeader("Authorization", "Basic: YWxhZGRpbjpvcGVuc2VzYW1l");
        context.getRequest().addHeader("Referer", "elastic.co");
        context.getRequest().addHeader("Cookie", "JESESSIONID=CAFEBABE");
        context.getResponse().addHeader("secret-token", "foo");
        context.getResponse().addHeader("Set-Cookie", "JESESSIONID=DEADBEEF");
        context.getResponse().addHeader("Content-Length", "-1");
    }

    private void assertContainsNoSensitiveInformation(TransactionContext context) {
        assertThat(context.getRequest().getCookies().get("JESESSIONID")).isEqualTo(REDACTED_CONTEXT_STRING);
        assertThat(context.getRequest().getCookies().get("non-sensitive")).isEqualTo("foo");

        assertThat(context.getRequest().getFormUrlEncodedParameters().get("cretidCard")).isEqualTo(REDACTED_CONTEXT_STRING);
        assertThat(context.getRequest().getFormUrlEncodedParameters().get("non-sensitive")).isEqualTo("foo");

        assertThat(context.getRequest().getHeaders().get("Authorization")).isEqualTo(REDACTED_CONTEXT_STRING);
        assertThat(context.getRequest().getHeaders().get("Cookie")).isNull();
        assertThat(context.getRequest().getHeaders().get("Referer")).isEqualTo("elastic.co");

        assertThat(context.getResponse().getHeaders().get("secret-token")).isEqualTo(REDACTED_CONTEXT_STRING);
        assertThat(context.getResponse().getHeaders().get("Set-Cookie")).isEqualTo(REDACTED_CONTEXT_STRING);
        assertThat(context.getResponse().getHeaders().get("Content-Length")).isEqualTo("-1");

    }

}
