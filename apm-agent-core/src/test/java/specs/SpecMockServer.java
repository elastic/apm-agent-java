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
package specs;

import co.elastic.apm.agent.report.ApmServerClient;
import co.elastic.apm.agent.report.HttpUtils;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.function.Consumer;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;

class SpecMockServer {

    private WireMockServer server = new WireMockServer(WireMockConfiguration.options()
        .extensions(new ResponseTemplateTransformer(false))
        .dynamicPort());

    private final String bodyTemplate;

    public SpecMockServer(String bodyTemplate){
        this.bodyTemplate = bodyTemplate;
    }

    public void start(){
        server.stubFor(get(urlEqualTo("/"))
            .willReturn(aResponse()
                .withTransformers("response-template")
                // just send back auth header (if any) for easy parsing on client side
                .withBody(bodyTemplate)));

        server.start();
    }

    public void stop() {
        server.stop();
    }

    @Deprecated
    public int port(){
        return server.port();
    }

    public URL getUrl(){
        try {
            return new URL(String.format("http://localhost:%d/", server.port()));
        } catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }
    }

    public void executeRequest(ApmServerClient client, Consumer<String> bodyCheck) {
        try {
            client.execute("/", new ApmServerClient.ConnectionHandler<Object>() {
                @Nullable
                @Override
                public Object withConnection(HttpURLConnection connection) throws IOException {
                    assertThat(connection.getResponseCode())
                        .describedAs("unexpected response code from server")
                        .isEqualTo(200);


                    String body = HttpUtils.readToString(connection.getInputStream());
                    bodyCheck.accept(body);

                    return null;
                }
            });
        } catch (Exception e) {
            fail(e.getMessage(), e);
        }
    }
}
