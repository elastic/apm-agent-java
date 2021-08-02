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
package co.elastic.apm.agent.sparkjava;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.transaction.Transaction;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import spark.Request;
import spark.Response;
import spark.Route;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static spark.Spark.awaitInitialization;
import static spark.Spark.get;
import static spark.Spark.init;
import static spark.Spark.port;
import static spark.Spark.stop;

class RoutesAdviceTest extends AbstractInstrumentationTest {

    private static OkHttpClient httpClient = new OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.SECONDS)
        .connectTimeout(0, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build();

    @BeforeAll
    static void startServer() {
        port(0);

        init();
        awaitInitialization();

        get("/foo/:bar", new Route() {
            @Override
            public Object handle(Request request, Response response) {
                return "bar";
            }
        });
    }

    @AfterAll
    static void stopServer() {
        stop();
    }

    @Test
    void testTransactionName() throws IOException {
        okhttp3.Request request = new okhttp3.Request.Builder().url("http://localhost:" + port() + "/foo/abc").build();
        okhttp3.Response response = httpClient.newCall(request).execute();
        assertThat(response.body().string()).isEqualTo("bar");

        Transaction transaction = reporter.getFirstTransaction(500);
        assertThat(transaction.getNameAsString()).isEqualTo("GET /foo/:bar");
        assertThat(transaction.getFrameworkName()).isEqualTo("Spark");
        assertThat(transaction.getFrameworkVersion()).isEqualTo("2.9.3");
    }
}
