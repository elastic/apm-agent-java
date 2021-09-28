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
import org.junit.jupiter.api.Test;

import javax.net.SocketFactory;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class SSLContextInstrumentationTest extends AbstractInstrumentationTest {

    private static final ThreadPoolExecutor elasticApmThreadPool = ExecutorUtils.createSingleThreadSchedulingDaemonPool("HttpsUrlConnection-Test");

    // note: we can't directly test that the default field is initialized or not by our agent
    // thus we have to test the "implementation detail" that calling any of the getDefault() methods should return
    // null when called from an agent thread to prevent eager initialization by the agent.w

    @Test
    void testNonSkipped() {
        checkDefaultFactory(false);
    }

    @Test
    void testSkipped() throws Exception {
        Future<?> connectionCreationFuture = elasticApmThreadPool.submit(() -> {
            checkDefaultFactory(true);
            createConnection();
            checkDefaultFactory(true);
        });
        connectionCreationFuture.get();
        checkDefaultFactory(false);
    }

    private void createConnection() {
        try {
            URLConnection urlConnection = new URL("https://localhost:11111").openConnection();
            assertThat(urlConnection).isInstanceOf(HttpsURLConnection.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void checkDefaultFactory(boolean expectNull) {
        try {
            Stream.of(SSLContext.getDefault(),
                    SocketFactory.getDefault(),
                    SocketFactory.getDefault())
                .forEach((d) -> {
                    if (expectNull) {
                        assertThat(d).isNull();
                    } else {
                        assertThat(d).isNotNull();
                    }
                });

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
