/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
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
package co.elastic.apm.agent.report.processor;

import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.stagemonitor.configuration.ConfigurationRegistry;

/**
 * A processor is executed right before a event (a {@link Transaction} or {@link Error}) gets reported.
 * <p>
 * You can use this for example to sanitize certain information.
 * </p>
 */
public interface Processor {

    /**
     * This method is called so that the processor can initialize configuration before the {@link #processBeforeReport} methods are called.
     *
     * @param configurationRegistry A reference to the {@link ConfigurationRegistry} which can be used to get configuration options.
     */
    void init(ConfigurationRegistry configurationRegistry);

    /**
     * This method is executed before the provided {@link Transaction} is reported.
     *
     * @param transaction The transaction to process.
     */
    void processBeforeReport(Transaction transaction);

    /**
     * This method is executed before the provided {@link ErrorCapture} is reported.
     *
     * @param error The error to process.
     */
    void processBeforeReport(ErrorCapture error);
}
