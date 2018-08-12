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
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stagemonitor.configuration.ConfigurationRegistry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class ReporterFactory {

    private static final Logger logger = LoggerFactory.getLogger(ReporterFactory.class);

    public Reporter createReporter(ConfigurationRegistry configurationRegistry, @Nullable String frameworkName,
                                   @Nullable String frameworkVersion) {
        final ReporterConfiguration reporterConfiguration = configurationRegistry.getConfig(ReporterConfiguration.class);
        final OkHttpClient httpClient = getOkHttpClient(reporterConfiguration);
        ExecutorService healthCheckExecutorService = Executors.newFixedThreadPool(1, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                final Thread thread = new Thread(r);
                thread.setName("apm-server-healthcheck");
                thread.setDaemon(true);
                return thread;
            }
        });
        healthCheckExecutorService.submit(new ApmServerHealthChecker(httpClient, reporterConfiguration));
        healthCheckExecutorService.shutdown();
        final ReportingEventHandler reportingEventHandler = getReportingEventHandler(configurationRegistry, frameworkName,
            frameworkVersion, reporterConfiguration, httpClient);
        return new ApmServerReporter(
            true, reporterConfiguration,
            configurationRegistry.getConfig(CoreConfiguration.class), reportingEventHandler);
    }

    @Nonnull
    private ReportingEventHandler getReportingEventHandler(ConfigurationRegistry configurationRegistry, @Nullable String frameworkName,
                                                           @Nullable String frameworkVersion, ReporterConfiguration reporterConfiguration,
                                                           OkHttpClient httpClient) {

        final DslJsonSerializer payloadSerializer = new DslJsonSerializer(
            configurationRegistry.getConfig(CoreConfiguration.class).isDistributedTracingEnabled(),
            configurationRegistry.getConfig(StacktraceConfiguration.class));
        final co.elastic.apm.impl.payload.Service service = new ServiceFactory().createService(configurationRegistry.getConfig(CoreConfiguration.class), frameworkName, frameworkVersion);
        final ApmServerHttpPayloadSender payloadSender = new ApmServerHttpPayloadSender(httpClient, payloadSerializer, reporterConfiguration);
        final ProcessInfo processInformation = ProcessFactory.ForCurrentVM.INSTANCE.getProcessInformation();
        final ProcessorEventHandler processorEventHandler = ProcessorEventHandler.loadProcessors(configurationRegistry);
        if (!reporterConfiguration.isIncludeProcessArguments()) {
            processInformation.getArgv().clear();
        }
        if (reporterConfiguration.isIntakeV2Enabled()) {
            return new IntakeV2ReportingEventHandler(service, processInformation, SystemInfo.create(), reporterConfiguration,
                processorEventHandler, payloadSerializer);
        } else {
            return new IntakeV1ReportingEventHandler(service, processInformation, SystemInfo.create(), payloadSender, reporterConfiguration,
                processorEventHandler);
        }
    }

    @Nonnull
    OkHttpClient getOkHttpClient(ReporterConfiguration reporterConfiguration) {
        final OkHttpClient.Builder builder = new OkHttpClient.Builder()
            .connectTimeout(reporterConfiguration.getServerTimeout(), TimeUnit.SECONDS);
        if (!reporterConfiguration.isVerifyServerCert()) {
            disableCertificateValidation(builder);
        }
        if (logger.isTraceEnabled()) {
            final HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
            loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.HEADERS);
            builder.addInterceptor(loggingInterceptor);
        }
        builder.addInterceptor(new Interceptor() {
            @Override
            public Response intercept(Chain chain) throws IOException {
                Request originalRequest = chain.request();
                Request requestWithUserAgent = originalRequest.newBuilder()
                    .header("User-Agent", getUserAgent())
                    .build();
                return chain.proceed(chain.request());
            }
        });
        return builder.build();
    }

    private String getUserAgent() {
        String agentVersion = VersionUtils.getAgentVersion();
        if (agentVersion != null) {
            return "apm-agent-java " + agentVersion;
        }
        return "apm-agent-java";
    }

    private void disableCertificateValidation(OkHttpClient.Builder builder) {
        final SSLSocketFactory sf = SslUtils.getTrustAllSocketFactory();
        if (sf != null) {
            builder
                .sslSocketFactory(sf, SslUtils.getTrustAllTrustManager())
                .hostnameVerifier(SslUtils.getTrustAllHostnameVerifyer());
        }
    }
}
