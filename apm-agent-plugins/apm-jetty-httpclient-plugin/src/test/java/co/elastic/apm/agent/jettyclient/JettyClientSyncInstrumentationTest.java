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
package co.elastic.apm.agent.jettyclient;

import co.elastic.apm.agent.httpclient.AbstractHttpClientInstrumentationTest;
import co.elastic.apm.agent.util.Version;
import co.elastic.apm.agent.util.VersionUtils;
import org.eclipse.jetty.client.HttpClient;
import org.junit.Before;

import java.util.Objects;


public class JettyClientSyncInstrumentationTest extends AbstractHttpClientInstrumentationTest {

    private HttpClient httpClient;
    private Version jettyClientVersion;

    @Before
    public void setUp() throws Exception {
        httpClient = new HttpClient();
        String versionString = VersionUtils.getVersion(HttpClient.class, "org.eclipse.jetty", "jetty-client");
        jettyClientVersion = Version.of(Objects.requireNonNullElse(versionString, "11.0.6"));
    }

    @Override
    protected void performGet(String path) throws Exception {
        httpClient.start();
        httpClient.GET(path);
        httpClient.stop();
    }

    @Override
    public boolean isNeedVerifyTraceContextAfterRedirect() {
        return jettyClientVersion.compareTo(Version.of("9.3.29")) > -1;
    }
}
