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
package co.elastic.apm.agent.report;

import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.metadata.MetaDataMock;
import co.elastic.apm.agent.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.report.serialize.DslJsonSerializer;
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
import org.stagemonitor.configuration.ConfigurationRegistry;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

class ReporterFactoryTest {

    private Server server;
    private ReporterFactory reporterFactory = new ReporterFactory();
    private ConfigurationRegistry configuration;
    private AtomicBoolean requestHandled = new AtomicBoolean(false);
    private ReporterConfiguration reporterConfiguration;

    @BeforeEach
    void setUp() throws Exception {
        server = new Server();

        Path keyStorePath = Paths.get(ReporterFactoryTest.class.getResource("/keystore").toURI());
        final SslContextFactory sslContextFactory = new SslContextFactory(keyStorePath.toAbsolutePath().toString());
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
        doReturn(Collections.singletonList(new URL("https://localhost:" + getPort()))).when(reporterConfiguration).getServerUrls();
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
        doReturn(false).when(reporterConfiguration).isVerifyServerCert();
        ApmServerClient apmServerClient = new ApmServerClient(reporterConfiguration, configuration.getConfig(CoreConfiguration.class));
        apmServerClient.start();
        DslJsonSerializer serializer = new DslJsonSerializer(configuration.getConfig(StacktraceConfiguration.class), apmServerClient, MetaDataMock.create());
        final Reporter reporter = reporterFactory.createReporter(configuration, apmServerClient, serializer, ReporterMonitor.NOOP);
        reporter.start();

        reporter.report(new Transaction(MockTracer.create()));
        reporter.flush();

        assertThat(requestHandled)
            .describedAs("request should ignore certificate validation and properly execute")
            .isTrue();
    }


    @Test
    void testValidatingSslCertificate() throws Exception {
        doReturn(true).when(reporterConfiguration).isVerifyServerCert();
        ApmServerClient apmServerClient = new ApmServerClient(reporterConfiguration, configuration.getConfig(CoreConfiguration.class));
        apmServerClient.start();
        DslJsonSerializer serializer = new DslJsonSerializer(configuration.getConfig(StacktraceConfiguration.class), apmServerClient, MetaDataMock.create());
        final Reporter reporter = reporterFactory.createReporter(configuration, apmServerClient, serializer, ReporterMonitor.NOOP);
        reporter.start();

        reporter.report(new Transaction(MockTracer.create()));
        reporter.flush();

        assertThat(requestHandled)
            .describedAs("request should have produced a certificate validation error")
            .isFalse();
    }
}
