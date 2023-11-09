package co.elastic.apm.agent.httpclient.v5;


import co.elastic.apm.agent.httpclient.AbstractHttpClientInstrumentationTest;
import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import java.io.IOException;

public class ApacheHttpClientInstrumentationTest extends AbstractHttpClientInstrumentationTest {

    private static CloseableHttpClient client;

    @BeforeClass
    public static void setUp() {
        client = HttpClients.createDefault();
    }

    @AfterClass
    public static void close() throws IOException {
        client.close();
    }

    /**
     * RFC 7230: treat presence of userinfo in authority component in request URI as an HTTP protocol violation.
     *
     * Uses {@link org.apache.hc.core5.http.message.BasicHttpRequest#setUri} to fill {@link org.apache.hc.core5.net.URIAuthority}
     *
     * Assertions on authority in {@link org.apache.hc.client5.http.impl.classic.ProtocolExec#execute}
     */
    @Override
    public boolean isTestHttpCallWithUserInfoEnabled() {
        return false;
    }

    @Override
    protected void performGet(String path) throws Exception {
        HttpClientResponseHandler<String> responseHandler = response -> {
            int status = response.getCode();
            if (status >= 200 && status < 300) {
                HttpEntity entity = response.getEntity();
                String res = entity != null ? EntityUtils.toString(entity) : null;
                return res;
            } else {
                throw new ClientProtocolException("Unexpected response status: " + status);
            }
        };
        String response = client.execute(new HttpGet(path), responseHandler);
    }
}
