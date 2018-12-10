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
package co.elastic.apm.report;

import co.elastic.apm.configuration.CoreConfiguration;
import co.elastic.apm.impl.payload.ProcessFactory;
import co.elastic.apm.impl.payload.ProcessInfo;
import co.elastic.apm.impl.payload.ServiceFactory;
import co.elastic.apm.impl.payload.SystemInfo;
import co.elastic.apm.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.report.processor.ProcessorEventHandler;
import co.elastic.apm.report.serialize.DslJsonSerializer;
import co.elastic.apm.util.VersionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stagemonitor.configuration.ConfigurationRegistry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class ReporterFactory {

    private static final Logger logger = LoggerFactory.getLogger(ReporterFactory.class);
    private final String userAgent = getUserAgent();

    public Reporter createReporter(ConfigurationRegistry configurationRegistry, @Nullable String frameworkName,
                                   @Nullable String frameworkVersion) {
        final ReporterConfiguration reporterConfiguration = configurationRegistry.getConfig(ReporterConfiguration.class);
        ExecutorService healthCheckExecutorService = Executors.newFixedThreadPool(1, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                final Thread thread = new Thread(r);
                thread.setName("apm-server-healthcheck");
                thread.setDaemon(true);
                return thread;
            }
        });
        healthCheckExecutorService.submit(new ApmServerHealthChecker(reporterConfiguration));
        healthCheckExecutorService.shutdown();
        final ReportingEventHandler reportingEventHandler = getReportingEventHandler(configurationRegistry, frameworkName,
            frameworkVersion, reporterConfiguration);
        return new ApmServerReporter(true, reporterConfiguration, reportingEventHandler);
    }

    @Nonnull
    private ReportingEventHandler getReportingEventHandler(ConfigurationRegistry configurationRegistry, @Nullable String frameworkName,
                                                           @Nullable String frameworkVersion, ReporterConfiguration reporterConfiguration) {

        final DslJsonSerializer payloadSerializer = new DslJsonSerializer(
            configurationRegistry.getConfig(StacktraceConfiguration.class));
        final co.elastic.apm.impl.payload.Service service = new ServiceFactory().createService(configurationRegistry.getConfig(CoreConfiguration.class), frameworkName, frameworkVersion);
        final ProcessInfo processInformation = ProcessFactory.ForCurrentVM.INSTANCE.getProcessInformation();
        final ProcessorEventHandler processorEventHandler = ProcessorEventHandler.loadProcessors(configurationRegistry);
        if (!reporterConfiguration.isIncludeProcessArguments()) {
            processInformation.getArgv().clear();
        }
        return new IntakeV2ReportingEventHandler(service, processInformation, SystemInfo.create(), reporterConfiguration,
            processorEventHandler, payloadSerializer);
    }

    private String getUserAgent() {
        String agentVersion = VersionUtils.getAgentVersion();
        if (agentVersion != null) {
            return "apm-agent-java " + agentVersion;
        }
        return "apm-agent-java";
    }
}
