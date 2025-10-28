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
package co.elastic.apm.agent.httpclient.v3;

import co.elastic.apm.agent.httpclient.AbstractHttpClientInstrumentationTest;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.junit.BeforeClass;
import org.junit.jupiter.api.condition.DisabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

@DisabledForJreRange(min = JRE.JAVA_25, disabledReason = "This is the legacy apache httpclient that is ancient (needs instrument_ancient_bytecode to apply), it fails after Java 25 and we simply stop supporting it after that")
public class HttpClient3InstrumentationTest extends AbstractHttpClientInstrumentationTest {

    private static HttpClient client;

    @BeforeClass
    public static void before() {
        client = new HttpClient();
    }

    @Override
    protected void performGet(String path) throws Exception {
        GetMethod getMethod = new GetMethod(path);
        client.executeMethod(getMethod);
        getMethod.getStatusLine().getStatusCode();
    }
}
