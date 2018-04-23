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
package co.elastic.apm.report;

import co.elastic.apm.configuration.CoreConfiguration;
import co.elastic.apm.impl.payload.ProcessFactory;
import co.elastic.apm.impl.payload.ServiceFactory;
import co.elastic.apm.impl.payload.SystemInfo;
import co.elastic.apm.report.processor.ProcessorEventHandler;
import co.elastic.apm.report.serialize.DslJsonSerializer;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stagemonitor.configuration.ConfigurationRegistry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

public class ReporterFactory {

    private static final Logger logger = LoggerFactory.getLogger(ReporterFactory.class);

    public Reporter createReporter(ConfigurationRegistry configurationRegistry, @Nullable String frameworkName,
                                   @Nullable String frameworkVersion) {
        final ReporterConfiguration reporterConfiguration = configurationRegistry.getConfig(ReporterConfiguration.class);
        return new ApmServerReporter(
            new ServiceFactory().createService(configurationRegistry.getConfig(CoreConfiguration.class), frameworkName, frameworkVersion),
            ProcessFactory.ForCurrentVM.INSTANCE.getProcessInformation(),
            SystemInfo.create(),
            new ApmServerHttpPayloadSender(getOkHttpClient(reporterConfiguration), new DslJsonSerializer(), reporterConfiguration),
            true, reporterConfiguration, ProcessorEventHandler.loadProcessors(configurationRegistry));
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
        return builder.build();
    }

    // based on https://gist.github.com/mefarazath/c9b588044d6bffd26aac3c520660bf40
    private void disableCertificateValidation(OkHttpClient.Builder builder) {
        // Create a trust manager that does not validate certificate chains
        final TrustManager[] trustAllCerts = new TrustManager[]{
            new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }
        };

        try {
            // Install the all-trusting trust manager
            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            // Create an ssl socket factory with our all-trusting manager
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();


            builder
                .sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0])
                .hostnameVerifier(new HostnameVerifier() {
                    @Override
                    public boolean verify(String hostname, SSLSession session) {
                        return true;
                    }
                });
        } catch (KeyManagementException | NoSuchAlgorithmException e) {
            logger.warn(e.getMessage(), e);
        }
    }
}
