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
package co.elastic.apm.agent.httpclient.v4;

import co.elastic.apm.agent.httpclient.AbstractHttpClientInstrumentationTest;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class LegacyApacheHttpClientHttpUriRequestInstrumentationTest extends AbstractHttpClientInstrumentationTest {

    @SuppressWarnings("deprecation")
    private static DefaultHttpClient client;

    @BeforeClass
    @SuppressWarnings("deprecation")
    public static void setUp() {
        client = new DefaultHttpClient();
    }

    @AfterClass
    public static void close() {
        client.getConnectionManager().shutdown();
    }

    @Override
    protected void performGet(String path) throws Exception {
        Method execute = client.getClass().getMethod("execute", HttpUriRequest.class);
        try {
            HttpResponse response = (HttpResponse) execute.invoke(client, new HttpGet(path));
            EntityUtils.consume(response.getEntity());
        } catch (InvocationTargetException e) {
            throw (Exception) e.getTargetException();
        }
    }
}
