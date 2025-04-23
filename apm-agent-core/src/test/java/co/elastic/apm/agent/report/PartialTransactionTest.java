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
package co.elastic.apm.agent.report;

import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.configuration.ServerlessConfigurationImpl;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.metadata.MetaDataMock;
import co.elastic.apm.agent.impl.transaction.TransactionImpl;
import co.elastic.apm.agent.objectpool.TestObjectPoolFactory;
import co.elastic.apm.agent.report.serialize.DslJsonSerializer;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.stagemonitor.configuration.ConfigurationRegistry;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

public class PartialTransactionTest {

    @RegisterExtension
    static WireMockExtension apmServer = WireMockExtension.newInstance()
        .options(wireMockConfig().dynamicPort())
        .build();

    private static ElasticApmTracer tracer;
    private static MockReporter reporter;
    private static TestObjectPoolFactory objectPoolFactory;
    private static ConfigurationRegistry spyConfig;

    @BeforeAll
    public static void setupTracer() {
        MockTracer.MockInstrumentationSetup mockSetup = MockTracer.createMockInstrumentationSetup();
        tracer = mockSetup.getTracer();
        reporter = mockSetup.getReporter();
        objectPoolFactory = mockSetup.getObjectPoolFactory();
        spyConfig = mockSetup.getConfig();

    }

    @BeforeEach
    public void setup() throws MalformedURLException {
        apmServer.stubFor(get(urlEqualTo("/"))
            .willReturn(aResponse().withBody("{\"version\" : \"8.7.0\"}")));

        ApmServerClient client = new ApmServerClient(spyConfig);
        client.start(List.of(new URL(apmServer.getRuntimeInfo().getHttpBaseUrl())));

        DslJsonSerializer serializer = new DslJsonSerializer(spyConfig, client, MetaDataMock.create());

        PartialTransactionReporter partialTransactionReporter = new PartialTransactionReporter(client, serializer, objectPoolFactory);
        reporter.setPartialTransactionHandler(partialTransactionReporter::reportPartialTransaction);
    }

    @AfterEach
    public void cleanup() {
        reporter.reset();
        objectPoolFactory.checkAllPooledObjectsHaveBeenRecycled();
        objectPoolFactory.reset();
    }

    @Test
    public void testReportAwsLambdaTransaction() {
        apmServer.stubFor(post(urlEqualTo("/register/transaction")).willReturn(aResponse().withStatus(200)));
        ServerlessConfigurationImpl serverlessConfig = spyConfig.getConfig(ServerlessConfigurationImpl.class);
        doReturn(true).when(serverlessConfig).runsOnAwsLambda();

        TransactionImpl tx1 = tracer
            .startRootTransaction(null)
            .withName("faas-transaction");
        tx1.getFaas().withExecution("foo-bar-id");

        tx1.activate();

        assertThat(apmServer.findAll(postRequestedFor(urlEqualTo("/register/transaction"))))
            .hasSize(1)
            .first()
            .satisfies(req -> {
                assertThat(req.getHeader("Content-Type")).isEqualTo("application/vnd.elastic.apm.transaction+ndjson");
                assertThat(req.getHeader("x-elastic-aws-request-id")).isEqualTo("foo-bar-id");

                String[] body = req.getBodyAsString().split("\n");
                assertThat(body).hasSize(2);

                String metadata = body[0];
                assertThatJson(metadata).inPath("$.metadata.service").isObject();
                assertThatJson(metadata).inPath("$.metadata.process").isObject();
                assertThatJson(metadata).inPath("$.metadata.system").isObject();

                String transaction = body[1];
                assertThatJson(transaction).inPath("$.transaction.timestamp").isEqualTo(tx1.getTimestamp());
                assertThatJson(transaction).inPath("$.transaction.id").isEqualTo(tx1.getTraceContext().getId().toString());
                assertThatJson(transaction).inPath("$.transaction.trace_id").isEqualTo(tx1.getTraceContext().getTraceId().toString());
                assertThatJson(transaction).inPath("$.transaction.faas.execution").isEqualTo("foo-bar-id");
            });

        tx1.deactivate();

        //ensure reporting only happens after first activation
        tx1.activate();
        tx1.deactivate().end();
        apmServer.verify(1, postRequestedFor(urlEqualTo("/register/transaction")));

        TransactionImpl tx2 = tracer
            .startRootTransaction(null)
            .withName("second-faas-transaction");
        tx2.getFaas().withExecution("baz-id");
        tx2.activate();

        assertThat(apmServer.findAll(postRequestedFor(urlEqualTo("/register/transaction"))))
            .hasSize(2)
            .element(1)
            .satisfies(req -> {
                assertThat(req.getHeader("Content-Type")).isEqualTo("application/vnd.elastic.apm.transaction+ndjson");
                assertThat(req.getHeader("x-elastic-aws-request-id")).isEqualTo("baz-id");

                String[] body = req.getBodyAsString().split("\n");
                assertThat(body).hasSize(2);

                String metadata = body[0];
                assertThatJson(metadata).inPath("$.metadata.service").isObject();
                assertThatJson(metadata).inPath("$.metadata.process").isObject();
                assertThatJson(metadata).inPath("$.metadata.system").isObject();

                String transaction = body[1];
                assertThatJson(transaction).inPath("$.transaction.timestamp").isEqualTo(tx2.getTimestamp());
                assertThatJson(transaction).inPath("$.transaction.id").isEqualTo(tx2.getTraceContext().getId().toString());
                assertThatJson(transaction).inPath("$.transaction.trace_id").isEqualTo(tx2.getTraceContext().getTraceId().toString());
                assertThatJson(transaction).inPath("$.transaction.faas.execution").isEqualTo("baz-id");
            });

        tx2.deactivate().end();
    }


    @Test
    public void testNoMoreReportingAfter4xx() {
        apmServer.stubFor(post(urlEqualTo("/register/transaction")).willReturn(aResponse().withStatus(404)));
        ServerlessConfigurationImpl serverlessConfig = spyConfig.getConfig(ServerlessConfigurationImpl.class);
        doReturn(true).when(serverlessConfig).runsOnAwsLambda();

        TransactionImpl tx1 = tracer
            .startRootTransaction(null)
            .withName("faas-transaction");
        tx1.getFaas().withExecution("foo-bar-id");
        tx1.activate();
        apmServer.verify(1, postRequestedFor(urlEqualTo("/register/transaction")));

        tx1.deactivate().end();

        TransactionImpl tx2 = tracer
            .startRootTransaction(null)
            .withName("faas-transaction");
        tx2.getFaas().withExecution("foo-bar-id");
        tx2.activate();
        tx2.deactivate().end();
        //There should be no more requests
        apmServer.verify(1, postRequestedFor(urlEqualTo("/register/transaction")));
    }


    @Test
    public void testNonLambdaTransactionNotReported() {
        apmServer.stubFor(post(urlEqualTo("/register/transaction")).willReturn(aResponse().withStatus(200)));
        ServerlessConfigurationImpl serverlessConfig = spyConfig.getConfig(ServerlessConfigurationImpl.class);
        doReturn(true).when(serverlessConfig).runsOnAwsLambda();

        TransactionImpl transaction = tracer.startRootTransaction(null).withName("nonfaas-transaction");
        transaction.activate();

        apmServer.verify(0, postRequestedFor(urlEqualTo("/register/transaction")));

        transaction.deactivate().end();
        apmServer.verify(0, postRequestedFor(urlEqualTo("/register/transaction")));
    }
}
