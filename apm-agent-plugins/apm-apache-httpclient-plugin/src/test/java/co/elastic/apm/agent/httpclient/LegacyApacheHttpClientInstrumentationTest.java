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
package co.elastic.apm.agent.httpclient;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.junit.AfterClass;
import org.junit.BeforeClass;

public class LegacyApacheHttpClientInstrumentationTest extends AbstractHttpClientInstrumentationTest {

    @SuppressWarnings("deprecation")
    private static DefaultHttpClient client;

    @BeforeClass
    @SuppressWarnings("deprecation")
    public static void setUp() {
        client = new DefaultHttpClient();
    }

    @AfterClass
    public static void close() {
        client.close();
    }

    @Override
    protected void performGet(String path) throws Exception {
        CloseableHttpResponse response = client.execute(new HttpGet(path));
        response.getStatusLine().getStatusCode();
        response.close();
    }
}
