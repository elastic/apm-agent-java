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
package co.elastic.apm.agent.finaglehttpclient;

import co.elastic.apm.agent.httpclient.AbstractHttpClientInstrumentationTest;
import co.elastic.apm.agent.impl.error.ErrorCapture;
import co.elastic.apm.agent.impl.transaction.Span;
import com.twitter.finagle.Http;
import com.twitter.finagle.Service;
import com.twitter.finagle.http.Request;
import com.twitter.finagle.http.RequestBuilder;
import com.twitter.finagle.http.Response;
import com.twitter.util.Future;
import org.junit.Ignore;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class FinagleHttpClientTest extends AbstractHttpClientInstrumentationTest {

    @Override
    protected boolean isRedirectFollowingSupported() {
        return false; // See https://github.com/twitter/finagle/issues/500
    }

    //This test currently fails to detect TLS because we rely on the "TlsFilter" to be part of the finagle service chain.
    //This is not always the case, e.g. when TLS is used without certificate verification
    @Test
    @Ignore
    public void getWithTlsWithoutVerification() throws Exception {
        String dest = "localhost:" + wireMockRule.httpsPort();
        Http.Client client = Http.client().withTlsWithoutValidation();
        Service<Request, Response> service = client.newService(dest);

        try {
            Future<Response> future = service.apply(Request.apply("/"));
            future.toCompletableFuture().get();
        } finally {
            service.close();
        }

        verifyHttpSpan("localhost", "/", 200, true, true, true);
    }

    @Test
    public void getWithTls() throws Exception {
        String dest = "localhost:" + wireMockRule.httpsPort();
        Http.Client client = Http.client().withTls("sub-host:" + wireMockRule.httpsPort());
        Service<Request, Response> service = client.newService(dest);

        try {
            Future<Response> future = service.apply(Request.apply("/"));
            assertThatThrownBy(() -> future.toCompletableFuture().get()).hasStackTraceContaining("SSLHandshakeException");
        } finally {
            service.close();
        }

        Span span = verifyHttpSpan("sub-host", "/", 0, false, true, false);
        assertThat(reporter.getErrors()).hasSize(1);
        ErrorCapture error = reporter.getFirstError();
        assertThat(error.getTraceContext().getTraceId()).isEqualTo(span.getTraceContext().getTraceId());
        assertThat(error.getTraceContext().getParentId()).isEqualTo(span.getTraceContext().getId());
        assertThat(error.getException()).hasStackTraceContaining("SSLHandshakeException");
    }


    @Test
    public void getWithoutHostHeader() throws Exception {
        Http.Client client = Http.client();
        String dest = "localhost:" + wireMockRule.port();
        Service<Request, Response> service = client.newService(dest);
        try {
            Future<Response> future = service.apply(Request.apply("/"));
            future.toCompletableFuture().get();
        } finally {
            service.close();
        }

        verifyHttpSpan("localhost", "/", 400, false);
    }


    @Override
    protected void performGet(String path) throws Exception {
        Request request = RequestBuilder.safeBuildGet(RequestBuilder.create().url(path));

        Service<Request, Response> service = (Service<Request, Response>) Http.newService("localhost:" + wireMockRule.port());
        try {
            service.apply(request).toCompletableFuture().get();
        } finally {
            service.close();
        }
    }
}
