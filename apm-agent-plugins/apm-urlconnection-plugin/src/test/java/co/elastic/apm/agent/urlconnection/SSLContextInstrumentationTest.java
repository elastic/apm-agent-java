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
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;

class SSLContextInstrumentationTest extends AbstractInstrumentationTest {

    // We can't really assert that the agent does not initialize the SSL context, but we can test how it's implemented
    // thus here we just check that trying to get default SSL context/factories is not possible from agent threads.

    private static final ThreadPoolExecutor elasticApmThreadPool = ExecutorUtils.createSingleThreadSchedulingDaemonPool("HttpsUrlConnection-Test");

    @Test
    void testNonSkipped() {
        createConnection(false);
    }

    @Test
    void testSkipped() throws Exception {
        Future<?> connectionCreationFuture = elasticApmThreadPool.submit(()-> createConnection(true));
        connectionCreationFuture.get();
    }

    private void createConnection(boolean expectNull) {
        try {
            SocketFactory defaultSslSocketFactory = SSLSocketFactory.getDefault();
            SSLContext defaultSslContext = SSLContext.getDefault();
            SocketFactory defaultSocketFactory = SocketFactory.getDefault();

            if (expectNull) {
                assertThat(defaultSslSocketFactory).isNull();
                assertThat(defaultSslContext).isNull();
                assertThat(defaultSocketFactory).isNull();
            } else {
                assertThat(defaultSslSocketFactory).isNotNull();
                assertThat(defaultSslContext).isNotNull();
                assertThat(defaultSocketFactory).isNotNull();
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
