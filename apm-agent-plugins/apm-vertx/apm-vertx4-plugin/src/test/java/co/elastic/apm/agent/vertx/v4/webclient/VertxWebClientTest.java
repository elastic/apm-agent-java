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
package co.elastic.apm.agent.vertx.v4.webclient;

import co.elastic.apm.agent.vertx.webclient.AbstractVertxWebClientTest;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.junit5.VertxTestContext;


public class VertxWebClientTest extends AbstractVertxWebClientTest {

    @Override
    protected void get(HttpRequest<Buffer> httpRequest, VertxTestContext testContext) {
        httpRequest.send().onComplete(testContext.succeedingThenComplete());
    }

    @Override
    protected void close(Vertx vertx) {
        vertx.close();
    }

    @Override
    protected void doVerifyFailedRequestHttpSpan(String host, String path) {
        // in Vert.x 4 a span is not created when the client fails - if we want to fix that, we can create a span in
        // the exit of HttpContext#prepareRequest(), but it would require fetching destination details from
        // io.vertx.ext.web.client.HttpRequest, which does not expose them, as opposed to io.vertx.core.http.HttpClientRequest
        // that is only get filled for the invocation of HttpContext#sendRequest()
    }
}
