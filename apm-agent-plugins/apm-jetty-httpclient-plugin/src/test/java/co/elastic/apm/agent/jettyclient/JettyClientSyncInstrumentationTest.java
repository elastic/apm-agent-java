package co.elastic.apm.agent.jettyclient;

import co.elastic.apm.agent.httpclient.AbstractHttpClientInstrumentationTest;
import org.eclipse.jetty.client.HttpClient;
import org.junit.Before;


public class JettyClientSyncInstrumentationTest extends AbstractHttpClientInstrumentationTest {

    private HttpClient httpClient;

    @Before
    public void setUp() throws Exception {
        httpClient = new HttpClient();
    }

    @Override
    protected void performGet(String path) throws Exception {
        httpClient.start();
        httpClient.newRequest(path).send();
        httpClient.stop();
    }
}
