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
package co.elastic.apm.agent.impl.context;

import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.report.processor.Processor;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import org.stagemonitor.configuration.ConfigurationRegistry;

import static co.elastic.apm.agent.configuration.CoreConfiguration.EventType.ALL;
import static co.elastic.apm.agent.configuration.CoreConfiguration.EventType.ERRORS;
import static co.elastic.apm.agent.configuration.CoreConfiguration.EventType.TRANSACTIONS;

/**
 * This processor redacts the body according to the {@link co.elastic.apm.agent.configuration.CoreConfiguration#captureBody}
 * configuration option
 */
@SuppressWarnings("JavadocReference")
public class BodyProcessor implements Processor {

    private final CoreConfiguration coreConfiguration;

    public BodyProcessor(ConfigurationRegistry configurationRegistry) {
        coreConfiguration = configurationRegistry.getConfig(CoreConfiguration.class);
    }

    @Override
    public void processBeforeReport(Transaction transaction) {
        redactBodyIfNecessary(transaction.getContext(), TRANSACTIONS);
    }

    @Override
    public void processBeforeReport(ErrorCapture error) {
        redactBodyIfNecessary(error.getContext(), ERRORS);
    }

    private void redactBodyIfNecessary(TransactionContext context, CoreConfiguration.EventType eventType) {
        final CoreConfiguration.EventType eventTypeConfig = coreConfiguration.getCaptureBody();
        if (eventTypeConfig != eventType && eventTypeConfig != ALL) {
            if (context.getRequest().getBody() != null) {
                context.getRequest().redactBody();
            }
            context.getMessage().redactBody();
        }
    }
}
