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
package co.elastic.apm.agent.google_http;

import co.elastic.apm.agent.httpclient.AbstractHttpClientInstrumentationTest;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.apache.ApacheHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.function.Supplier;

@RunWith(Parameterized.class)
public class HttpRequestInstrumentationTest extends AbstractHttpClientInstrumentationTest {

    private final HttpRequestFactory httpRequestFactory;

    public HttpRequestInstrumentationTest(Supplier<HttpTransport> httpTransportSupplier) {
        httpRequestFactory = httpTransportSupplier.get().createRequestFactory();
    }

    @Parameterized.Parameters()
    public static Iterable<Supplier<HttpTransport>> data() {
        return Arrays.asList(
            ApacheHttpTransport::new,
            NetHttpTransport::new
        );
    }

    @Override
    protected boolean isErrorOnCircularRedirectSupported() {
        return false;
    }

    @Override
    protected void performGet(String path) throws Exception {
        httpRequestFactory.buildGetRequest(new GenericUrl(path)).execute();
    }
}
