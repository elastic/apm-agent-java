package co.elastic.apm.report;

import okhttp3.OkHttpClient;
import okhttp3.Response;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReporterFactoryTest {

    private Server server;
    private ReporterFactory reporterFactory = new ReporterFactory();
    private ReporterConfiguration configuration;

    @BeforeEach
    void setUp() throws Exception {
        server = new Server();
        configuration = mock(ReporterConfiguration.class);

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
            }
        });
        server.start();
    }

    private int getPort() {
        return ((NetworkConnector) server.getConnectors()[0]).getLocalPort();
    }

    @Test
    // Jenkins fails with java.lang.IllegalStateException: no valid keystore
    // tbh, I have no clue why
    @DisabledIfEnvironmentVariable(named = "JENKINS_HOME", matches = ".*")
    void testNotValidatingSslCertificate() throws IOException {
        when(configuration.isVerifyServerCert()).thenReturn(false);

        final OkHttpClient okHttpClient = reporterFactory.getOkHttpClient(configuration);
        final Response response = okHttpClient.newCall(new okhttp3.Request.Builder().url("https://localhost:" + getPort()).build())
            .execute();
        assertThat(response.code()).isEqualTo(200);
    }
}
