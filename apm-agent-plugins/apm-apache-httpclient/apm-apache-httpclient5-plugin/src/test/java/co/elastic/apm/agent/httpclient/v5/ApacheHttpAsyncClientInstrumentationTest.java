package co.elastic.apm.agent.httpclient.v5;

import co.elastic.apm.agent.httpclient.AbstractHttpClientInstrumentationTest;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.classic.ProtocolExec;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.assertj.core.api.Assertions;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import static co.elastic.apm.agent.testutils.assertions.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ApacheHttpAsyncClientInstrumentationTest extends AbstractHttpClientInstrumentationTest {

    private static CloseableHttpAsyncClient client;

    @BeforeClass
    public static void setUp() {
        client = HttpAsyncClients.createDefault();
        client.start();
    }

    @AfterClass
    public static void tearDown() throws IOException {
        client.close();
    }

    @Override
    protected void performGet(String path) throws Exception {
        final CompletableFuture<SimpleHttpResponse> responseFuture = new CompletableFuture<>();

        SimpleHttpRequest req = SimpleRequestBuilder.get().setPath(path).build();
        RequestConfig requestConfig = RequestConfig.custom()
            .setCircularRedirectsAllowed(true)
            .build();
        HttpClientContext httpClientContext = HttpClientContext.create();
        httpClientContext.setRequestConfig(requestConfig);
        client.execute(req, httpClientContext, new FutureCallback<SimpleHttpResponse>() {
            @Override
            public void completed(SimpleHttpResponse simpleHttpResponse) {
                responseFuture.complete(simpleHttpResponse);
            }

            @Override
            public void failed(Exception e) {
                responseFuture.completeExceptionally(e);
            }

            @Override
            public void cancelled() {
                responseFuture.cancel(true);
            }
        });

        responseFuture.get();
    }


    @Test
    public void testSpanFinishOnEarlyException() throws Exception {

        client.close(); //this forces execute to immediately exit with an exception

        reporter.disableCheckServiceTarget();
        reporter.disableCheckDestinationAddress();
        try {
            assertThatThrownBy(() -> performGet(getBaseUrl() + "/")).isInstanceOf(IllegalStateException.class);
        } finally {
            //Reset state for other tests
            setUp();
            reporter.resetChecks();
        }
        assertThat(reporter.getFirstSpan(500)).isNotNull();
        Assertions.assertThat(reporter.getSpans()).hasSize(1);
    }


    /**
     * org.apache.hc.client5.http.ClientProtocolException: Request URI authority contains deprecated userinfo component
     * see {@link ProtocolExec#execute}
     *   final URIAuthority authority = request.getAuthority();
     *   if (authority.getUserInfo() != null) {
     *      throw new ProtocolException("Request URI authority contains deprecated userinfo component");
     *   }
     */
//    @Override
//    public String getBaseUserInfoPath() {
//        return "http://localhost:";
//    }
}
