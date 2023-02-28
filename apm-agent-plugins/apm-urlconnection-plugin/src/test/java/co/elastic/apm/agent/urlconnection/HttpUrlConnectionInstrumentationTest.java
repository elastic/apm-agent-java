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
package co.elastic.apm.agent.urlconnection;

import co.elastic.apm.agent.httpclient.AbstractHttpClientInstrumentationTest;
import co.elastic.apm.agent.impl.context.Http;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.tracer.Scope;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

public class HttpUrlConnectionInstrumentationTest extends AbstractHttpClientInstrumentationTest {

    @Override
    protected void performGet(String path) throws Exception {
        final HttpURLConnection urlConnection = (HttpURLConnection) new URL(path).openConnection();
        urlConnection.getInputStream();
        urlConnection.disconnect();
    }

    @Test
    public void testEndInDifferentThread() throws Exception {
        final HttpURLConnection urlConnection = (HttpURLConnection) new URL(getBaseUrl() + "/").openConnection();
        urlConnection.connect();
        AbstractSpan<?> active = tracer.getActive();
        final Thread thread = new Thread(() -> {
            try (Scope scope = active.activateInScope()) {
                urlConnection.getInputStream();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        thread.start();
        thread.join();

        verifyHttpSpan("/");
    }

    @Test
    public void testDisconnectionWithoutExecute() throws Exception {
        final HttpURLConnection urlConnection = (HttpURLConnection) new URL(getBaseUrl() + "/").openConnection();
        urlConnection.connect();
        urlConnection.disconnect();
        assertThat(reporter.getSpans()).hasSize(1);
        assertThat(reporter.getSpans().get(0).getContext().getHttp().getStatusCode()).isEqualTo(0);
    }

    @Test
    public void testMultipleConnect() throws Exception {
        final HttpURLConnection urlConnection = (HttpURLConnection) new URL(getBaseUrl() + "/").openConnection();
        urlConnection.connect();
        urlConnection.connect();
        urlConnection.getInputStream();

        verifyHttpSpan("/");
    }

    @Test
    public void testGetOutputStream() throws Exception {
        final HttpURLConnection urlConnection = (HttpURLConnection) new URL(getBaseUrl() + "/").openConnection();
        urlConnection.setDoOutput(true);
        urlConnection.getOutputStream();
        urlConnection.getInputStream();

        verifyHttpSpan("/");
    }

    @Test
    public void testConnectAfterInputStream() throws Exception {
        final HttpURLConnection urlConnection = (HttpURLConnection) new URL(getBaseUrl() + "/").openConnection();
        urlConnection.getInputStream();
        // should not create another span
        // works because the connected flag is checked
        urlConnection.connect();
        urlConnection.disconnect();

        verifyHttpSpan("/");
    }

    @Test
    @Ignore
    public void testFakeReuse() throws Exception {
        final HttpURLConnection urlConnection = (HttpURLConnection) new URL(getBaseUrl() + "/").openConnection();
        urlConnection.getInputStream();
        urlConnection.disconnect();

        // reusing HttpURLConnection instances is not supported
        // the following calls will essentially be noops
        // but the agent wrongly creates spans
        // however, we don't consider this to be a big problem
        // as it is unlikely someone uses it that way and because the consequences are not severe
        // there is a span being created, but the activation does not leak
        urlConnection.getInputStream();
        urlConnection.disconnect();

        verify(1, getRequestedFor(urlPathEqualTo("/")));
        verifyHttpSpan("/");
    }

    @Test
    public void testGetResponseCodeWithUnhandledException() {
        try {
            final HttpURLConnection urlConnection = (HttpURLConnection) new URL("http://unknown").openConnection();
            urlConnection.getResponseCode();
            urlConnection.disconnect();
        } catch (Exception e) {
            // intentionally ignored
        }

        assertThat(reporter.getErrors()).hasSize(1);

        assertThat(reporter.getFirstSpan(500)).isNotNull();
        assertThat(reporter.getSpans()).hasSize(1);

        Span span = reporter.getSpans().get(0);
        assertThat(span.getNameAsString()).isEqualTo("GET unknown");

        Http http = span.getContext().getHttp();
        assertThat(http.getStatusCode()).isEqualTo(0);
        assertThat(http.getUrl().toString()).isEqualTo("http://unknown");
    }

    @Test
    public void testGetResponseWithHandledException() throws Exception {
        final HttpURLConnection urlConnection = (HttpURLConnection) new URL(getBaseUrl() + "/non-existent").openConnection();
        urlConnection.getResponseCode();
        urlConnection.disconnect();

        verifyHttpSpan("localhost", "/non-existent", 404);
        assertThat(reporter.getErrors()).isEmpty();
    }

    @Test
    public void testGetInstrumentationWithErrorEvent() {
        String path = "/non-existing";
        performGetWithinTransaction(path);

        verifyHttpSpan("localhost", path, 404);
        assertThat(reporter.getErrors()).hasSize(1);
    }

}
