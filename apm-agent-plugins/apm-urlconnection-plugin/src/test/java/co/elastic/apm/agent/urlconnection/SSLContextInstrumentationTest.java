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
package co.elastic.apm.agent.urlconnection;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.util.ExecutorUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;

class SSLContextInstrumentationTest extends AbstractInstrumentationTest {

    private static Field defaultSSLSocketFactoryField;
    private static ThreadPoolExecutor elasticApmThreadPool = ExecutorUtils.createSingleThreadSchedulingDaemonPool("HttpsUrlConnection-Test");

    @BeforeAll
    static void setup() throws Exception {
        defaultSSLSocketFactoryField = HttpsURLConnection.class.getDeclaredField("defaultSSLSocketFactory");
        defaultSSLSocketFactoryField.setAccessible(true);
        defaultSSLSocketFactoryField.set(null, null);
    }

    @BeforeEach
    void resetState() throws Exception {
        defaultSSLSocketFactoryField.set(null, null);
    }

    @Test
    void testNonSkipped() throws Exception {
        createConnection();
        Object defaultSslFactory = defaultSSLSocketFactoryField.get(null);
        assertThat(defaultSslFactory).isNotNull();
        assertThat(defaultSslFactory).isEqualTo(HttpsURLConnection.getDefaultSSLSocketFactory());
    }

    @Test
    void testSkipped() throws Exception {
        Future<?> connectionCreationFuture = elasticApmThreadPool.submit(this::createConnection);
        connectionCreationFuture.get();
        assertThat(defaultSSLSocketFactoryField.get(null)).isNull();
        createConnection();
        assertThat(defaultSSLSocketFactoryField.get(null)).isNotNull();
    }

    private void createConnection() {
        try {
            URLConnection urlConnection = new URL("https://localhost:11111").openConnection();
            assertThat(urlConnection).isInstanceOf(HttpsURLConnection.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
