package co.elastic.apm.agent.jettyclient;

import co.elastic.apm.agent.httpclient.AbstractHttpClientInstrumentationTest;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.api.Result;
import org.junit.Before;


public class JettyClientASyncInstrumentationTest extends AbstractHttpClientInstrumentationTest {

    private HttpClient httpClient;

    @Before
    public void setUp() throws Exception {
        httpClient = new HttpClient();
    }

    @Override
    protected void performGet(String path) throws Exception {
        httpClient.start();
        httpClient.newRequest(path)
            .send(new Response.CompleteListener() {
                @Override
                public void onComplete(Result result) {
                    System.out.println(String.format("Got response with status = %s", result.getResponse().getStatus()));
                }
            });
        httpClient.stop();
    }
}
