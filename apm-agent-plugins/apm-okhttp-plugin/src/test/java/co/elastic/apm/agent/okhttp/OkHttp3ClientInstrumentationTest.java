/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.agent.okhttp;

import co.elastic.apm.agent.httpclient.AbstractHttpClientInstrumentationTest;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.junit.Before;

public class OkHttp3ClientInstrumentationTest extends AbstractHttpClientInstrumentationTest {

    private OkHttpClient client;

    @Before
    public void setUp() {
        client = new OkHttpClient();
    }

    @Override
    protected void performGet(String path) throws Exception {
        Request request = new Request.Builder()
            .url(path)
            .build();
        client.newCall(request).execute();
    }

}

