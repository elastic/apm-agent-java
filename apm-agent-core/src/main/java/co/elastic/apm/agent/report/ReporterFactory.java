/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.agent.report;

import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.impl.MetaData;
import co.elastic.apm.agent.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.agent.report.processor.ProcessorEventHandler;
import co.elastic.apm.agent.report.serialize.DslJsonSerializer;
import org.stagemonitor.configuration.ConfigurationRegistry;

import javax.annotation.Nonnull;

public class ReporterFactory {

    public Reporter createReporter(ConfigurationRegistry configurationRegistry, ApmServerClient apmServerClient, MetaData metaData) {
        final ReporterConfiguration reporterConfiguration = configurationRegistry.getConfig(ReporterConfiguration.class);
        final CoreConfiguration coreConfiguration = configurationRegistry.getConfig(CoreConfiguration.class);
        final ReportingEventHandler reportingEventHandler = getReportingEventHandler(configurationRegistry,
            reporterConfiguration, metaData, apmServerClient);
        return new ApmServerReporter(true, reporterConfiguration, coreConfiguration, reportingEventHandler);
    }

    @Nonnull
    private ReportingEventHandler getReportingEventHandler(ConfigurationRegistry configurationRegistry,
                                                           ReporterConfiguration reporterConfiguration, MetaData metaData, ApmServerClient apmServerClient) {

        final DslJsonSerializer payloadSerializer = new DslJsonSerializer(configurationRegistry.getConfig(StacktraceConfiguration.class), apmServerClient);
        final ProcessorEventHandler processorEventHandler = ProcessorEventHandler.loadProcessors(configurationRegistry);
        return new IntakeV2ReportingEventHandler(reporterConfiguration, processorEventHandler, payloadSerializer, metaData, apmServerClient);
    }

}
