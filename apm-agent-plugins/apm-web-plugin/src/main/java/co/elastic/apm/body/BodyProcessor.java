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
package co.elastic.apm.body;

import co.elastic.apm.configuration.WebConfiguration;
import co.elastic.apm.impl.context.Context;
import co.elastic.apm.impl.context.Request;
import co.elastic.apm.impl.error.ErrorCapture;
import co.elastic.apm.impl.transaction.Transaction;
import co.elastic.apm.report.processor.Processor;
import org.stagemonitor.configuration.ConfigurationRegistry;

import static co.elastic.apm.configuration.WebConfiguration.EventType.ALL;
import static co.elastic.apm.configuration.WebConfiguration.EventType.ERRORS;
import static co.elastic.apm.configuration.WebConfiguration.EventType.TRANSACTIONS;

/**
 * This processor redacts the body according to the {@link WebConfiguration#captureBody} configuration option
 */
public class BodyProcessor implements Processor {

    private WebConfiguration webConfiguration;

    @Override
    public void init(ConfigurationRegistry configurationRegistry) {
        webConfiguration = configurationRegistry.getConfig(WebConfiguration.class);
    }

    @Override
    public void processBeforeReport(Transaction transaction) {
        redactBodyIfNecessary(transaction.getContext(), TRANSACTIONS);
    }

    @Override
    public void processBeforeReport(ErrorCapture error) {
        redactBodyIfNecessary(error.getContext(), ERRORS);
    }

    private void redactBodyIfNecessary(Context context, WebConfiguration.EventType eventType) {
        final WebConfiguration.EventType eventTypeConfig = webConfiguration.getCaptureBody();
        if (hasBody(context.getRequest()) && eventTypeConfig != eventType && eventTypeConfig != ALL) {
            context.getRequest().redactBody();
        }
    }

    private boolean hasBody(Request request) {
        return request.getBody() != null;
    }
}
