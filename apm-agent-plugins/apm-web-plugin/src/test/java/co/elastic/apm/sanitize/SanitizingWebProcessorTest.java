/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 the original author or authors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.sanitize;

import co.elastic.apm.configuration.SpyConfiguration;
import co.elastic.apm.impl.context.Context;
import co.elastic.apm.impl.error.ErrorCapture;
import co.elastic.apm.impl.transaction.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SanitizingWebProcessorTest {

    private SanitizingWebProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new SanitizingWebProcessor();
        processor.init(SpyConfiguration.createSpyConfig());
    }

    @Test
    void processTransactions() {
        Transaction transaction = new Transaction();
        fillContext(transaction.getContext());

        processor.processBeforeReport(transaction);

        assertContainsNoSensitiveInformation(transaction.getContext());
    }

    @Test
    void processErrors() {
        final ErrorCapture errorCapture = new ErrorCapture();
        fillContext(errorCapture.getContext());

        processor.processBeforeReport(errorCapture);

        assertContainsNoSensitiveInformation(errorCapture.getContext());
    }

    private void fillContext(Context context) {
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

    private void assertContainsNoSensitiveInformation(Context context) {
        assertThat(context.getRequest().getCookies())
            .containsEntry("JESESSIONID", SanitizingWebProcessor.REDACTED)
            .containsEntry("non-sensitive", "foo");
        assertThat(context.getRequest().getFormUrlEncodedParameters())
            .containsEntry("cretidCard", SanitizingWebProcessor.REDACTED)
            .containsEntry("non-sensitive", "foo");
        assertThat(context.getRequest().getHeaders())
            .containsEntry("Authorization", SanitizingWebProcessor.REDACTED)
            .doesNotContainKey("Cookie")
            .containsEntry("Referer", "elastic.co");
        assertThat(context.getResponse().getHeaders())
            .containsEntry("secret-token", SanitizingWebProcessor.REDACTED)
            .containsEntry("Set-Cookie", SanitizingWebProcessor.REDACTED)
            .containsEntry("Content-Length", "-1");
    }

}
