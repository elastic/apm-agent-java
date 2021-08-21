package co.elastic.apm.agent.jettyclient;

import co.elastic.apm.agent.httpclient.AbstractHttpClientInstrumentationTest;
import co.elastic.apm.agent.util.Version;
import co.elastic.apm.agent.util.VersionUtils;
import org.eclipse.jetty.client.HttpClient;
import org.junit.Before;

import java.util.Objects;


public class JettyClientSyncInstrumentationTest extends AbstractHttpClientInstrumentationTest {

    private HttpClient httpClient;
    private Version jettyClientVersion;

    @Before
    public void setUp() throws Exception {
        httpClient = new HttpClient();
        String versionString = VersionUtils.getVersion(HttpClient.class, "org.eclipse.jetty", "jetty-client");
        jettyClientVersion = Version.of(Objects.requireNonNullElse(versionString, "11.0.6"));
    }

    @Override
    protected void performGet(String path) throws Exception {
        httpClient.start();
        httpClient.GET(path);
        httpClient.stop();
    }

    @Override
    public boolean isNeedVerifyTraceContextAfterRedirect() {
        return jettyClientVersion.compareTo(Version.of("9.3.29")) > -1;
    }
}
