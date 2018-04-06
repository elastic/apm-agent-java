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

import co.elastic.apm.configuration.CoreConfiguration;
import co.elastic.apm.configuration.SpyConfiguration;
import co.elastic.apm.impl.context.Context;
import co.elastic.apm.impl.error.ErrorCapture;
import co.elastic.apm.impl.transaction.Transaction;
import co.elastic.apm.matcher.WildcardMatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.stagemonitor.configuration.ConfigurationRegistry;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class SanitizingCoreProcessorTest {

    private SanitizingCoreProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new SanitizingCoreProcessor();
        final ConfigurationRegistry config = SpyConfiguration.createSpyConfig();
        CoreConfiguration coreConfig = config.getConfig(CoreConfiguration.class);
        when(coreConfig.getSanitizeFieldNames()).thenReturn(Collections.singletonList(WildcardMatcher.valueOf("sensitive*")));
        processor.init(config);
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
        context.getTags().put("regularFoo", "bar");
        context.getTags().put("sensitiveFoo", "bar");
        context.getCustom().put("regularFoo", "bar");
        context.getCustom().put("sensitiveFoo", "bar");
    }

    private void assertContainsNoSensitiveInformation(Context context) {
        assertThat(context.getTags())
            .containsEntry("sensitiveFoo", AbstractSanitizingProcessor.REDACTED)
            .containsEntry("regularFoo", "bar")
            .doesNotContainEntry("sensitiveFoo", "bar");
        assertThat(context.getCustom())
            .containsEntry("sensitiveFoo", AbstractSanitizingProcessor.REDACTED)
            .containsEntry("regularFoo", "bar")
            .doesNotContainEntry("sensitiveFoo", "bar");
    }
}
