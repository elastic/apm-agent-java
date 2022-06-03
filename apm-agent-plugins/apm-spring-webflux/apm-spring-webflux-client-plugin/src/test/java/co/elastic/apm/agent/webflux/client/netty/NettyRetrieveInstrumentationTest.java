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
package co.elastic.apm.agent.webflux.client.netty;

import co.elastic.apm.agent.webflux.client.AbstractWebClientInstrumentationTest;
import org.springframework.web.reactive.function.client.WebClient;

public class NettyRetrieveInstrumentationTest extends AbstractWebClientInstrumentationTest {

    @Override
    protected WebClient createClient() {
        return NettyClient.createClient();
    }

    @Override
    public boolean isRequireCheckErrorWhenCircularRedirect() {
        // circular redirect does not trigger an error to capture with netty
        return false;
    }

    @Override
    public boolean isTestHttpCallWithUserInfoEnabled() {
        // user credentials in URI are not supported, we get the following error when trying to:
        // WebClientRequestException: failed to resolve 'user:passwd@localhost'
        return false;
    }

    @Override
    protected void performGet(String uri) throws Exception {
        retrieveGet(uri);
    }
}
