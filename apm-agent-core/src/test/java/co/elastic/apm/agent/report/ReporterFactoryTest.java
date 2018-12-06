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
package co.elastic.apm.agent.report;

import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.NetworkConnector;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.stagemonitor.configuration.ConfigurationRegistry;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.URL;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// Jenkins fails with java.lang.IllegalStateException: no valid keystore
// tbh, I have no clue why
@DisabledIfEnvironmentVariable(named = "JENKINS_HOME", matches = ".*")
class ReporterFactoryTest {

    private Server server;
    private ReporterFactory reporterFactory = new ReporterFactory();
    private ConfigurationRegistry configuration;
    private AtomicBoolean requestHandled = new AtomicBoolean(false);
    private ReporterConfiguration reporterConfiguration;

    @BeforeEach
    void setUp() throws Exception {
        server = new Server();

        final SslContextFactory sslContextFactory = new SslContextFactory(getClass().getResource("/keystore").getPath());
        sslContextFactory.setKeyStorePassword("password");
        sslContextFactory.getSslContext();

        final HttpConfiguration httpConfiguration = new HttpConfiguration();
        httpConfiguration.setSecureScheme("https");
        httpConfiguration.setSecurePort(0);

        final HttpConfiguration httpsConfiguration = new HttpConfiguration(httpConfiguration);
        httpsConfiguration.addCustomizer(new SecureRequestCustomizer());
        final ServerConnector httpsConnector = new ServerConnector(server,
            new SslConnectionFactory(sslContextFactory, HttpVersion.HTTP_1_1.asString()),
            new HttpConnectionFactory(httpsConfiguration));
        httpsConnector.setPort(0);
        server.addConnector(httpsConnector);
        server.setHandler(new AbstractHandler() {
            @Override
            public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) {
                baseRequest.setHandled(true);
                requestHandled.set(true);
            }
        });
        server.start();
        configuration = SpyConfiguration.createSpyConfig();
        reporterConfiguration = configuration.getConfig(ReporterConfiguration.class);
        when(reporterConfiguration.getServerUrls()).thenReturn(Collections.singletonList(new URL("https://localhost:" + getPort())));
    }

    @AfterEach
    void tearDown() throws Exception {
        server.stop();
    }

    private int getPort() {
        return ((NetworkConnector) server.getConnectors()[0]).getLocalPort();
    }

    @Test
    void testNotValidatingSslCertificate() throws Exception {
        when(reporterConfiguration.isVerifyServerCert()).thenReturn(false);
        final Reporter reporter = reporterFactory.createReporter(configuration, null, null);

        reporter.report(new Transaction(mock(ElasticApmTracer.class)));
        reporter.flush().get();

        assertThat(requestHandled).isTrue();
    }


    @Test
    void testValidatingSslCertificate() throws Exception {
        when(reporterConfiguration.isVerifyServerCert()).thenReturn(true);
        final Reporter reporter = reporterFactory.createReporter(configuration, null, null);

        reporter.report(new Transaction(mock(ElasticApmTracer.class)));
        reporter.flush().get();

        assertThat(requestHandled).isFalse();
    }
}
