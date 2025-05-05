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
package co.elastic.apm.agent.esrestclient.v6_4;

import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.configuration.CoreConfigurationImpl;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.ElasticApmTracerBuilder;
import co.elastic.apm.agent.impl.metadata.Agent;
import co.elastic.apm.agent.impl.metadata.MetaDataMock;
import co.elastic.apm.agent.impl.metadata.ProcessInfo;
import co.elastic.apm.agent.impl.metadata.ServiceImpl;
import co.elastic.apm.agent.impl.metadata.SystemInfo;
import co.elastic.apm.agent.impl.stacktrace.StacktraceConfigurationImpl;
import co.elastic.apm.agent.impl.transaction.TransactionImpl;
import co.elastic.apm.agent.objectpool.ObjectPoolFactoryImpl;
import co.elastic.apm.agent.report.ApmServerClient;
import co.elastic.apm.agent.report.ApmServerReporter;
import co.elastic.apm.agent.report.IntakeV2ReportingEventHandler;
import co.elastic.apm.agent.report.Reporter;
import co.elastic.apm.agent.report.ReporterConfigurationImpl;
import co.elastic.apm.agent.report.ReporterMonitor;
import co.elastic.apm.agent.report.processor.ProcessorEventHandler;
import co.elastic.apm.agent.report.serialize.DslJsonSerializer;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.stagemonitor.configuration.ConfigurationRegistry;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.mockito.Mockito.doReturn;

@Ignore
public class ElasticsearchRestClientInstrumentationIT_RealReporter {
    private static final String USER_NAME = "elastic-user";
    private static final String PASSWORD = "elastic-pass";

    @SuppressWarnings("NullableProblems")
    private static ElasticsearchContainer container;
    @SuppressWarnings("NullableProblems")
    private static RestHighLevelClient client;

    private static final String INDEX = "my-index";
    private static final String SECOND_INDEX = "my-second-index";
    private static final String DOC_ID = "38uhjds8s4g";
    private static final String DOC_TYPE = "_doc";
    private static final String FOO = "foo";
    private static final String BAR = "bar";
    private static final String BAZ = "baz";

    private static ElasticApmTracer tracer;
    private static Reporter realReporter;

    /**
     * A version of ElasticsearchRestClientInstrumentationIT that can be used to test E2E the ES client instrumentation with the IT stack
     */
    @BeforeClass
    public static void startElasticsearchContainerAndClient() throws IOException {
        // Start the container
        container = new ElasticsearchContainer();

        // There is currently no way to change the ES port on the testcontainer, so it will conflict with the IT ES. So we just use the IT ES
        // container.start();

        // Create the client
        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(USER_NAME, PASSWORD));

        RestClientBuilder builder = RestClient.builder(new HttpHost("localhost", 9200))
            .setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider));
        client = new RestHighLevelClient(builder);

        client.indices().create(new CreateIndexRequest(INDEX), RequestOptions.DEFAULT);

        final ConfigurationRegistry configurationRegistry = SpyConfiguration.createSpyConfig();
        ReporterConfigurationImpl reporterConfiguration = configurationRegistry.getConfig(ReporterConfigurationImpl.class);
        CoreConfigurationImpl coreConfiguration = configurationRegistry.getConfig(CoreConfigurationImpl.class);
        doReturn(0).when(reporterConfiguration).getMaxQueueSize();
        StacktraceConfigurationImpl stacktraceConfiguration = configurationRegistry.getConfig(StacktraceConfigurationImpl.class);
        doReturn(30).when(stacktraceConfiguration).getStackTraceLimit();
        SystemInfo system = new SystemInfo("x64", "localhost", null, "platform");
        final ServiceImpl service = new ServiceImpl().withName("Eyal-ES-client-test").withAgent(new Agent("java", "Test"));
        final ProcessInfo title = new ProcessInfo("title");
        final ProcessorEventHandler processorEventHandler = ProcessorEventHandler.loadProcessors(configurationRegistry);
        ApmServerClient apmServerClient = new ApmServerClient(configurationRegistry);
        apmServerClient.start();
        DslJsonSerializer payloadSerializer = new DslJsonSerializer(
            SpyConfiguration.createSpyConfig(),
            apmServerClient,
            MetaDataMock.create(title, service, system, null, Collections.emptyMap(), null)
        );
        final IntakeV2ReportingEventHandler v2handler = new IntakeV2ReportingEventHandler(
            reporterConfiguration,
            processorEventHandler,
            payloadSerializer,
            apmServerClient);
        realReporter = new ApmServerReporter(true, reporterConfiguration, coreConfiguration, v2handler, ReporterMonitor.NOOP, apmServerClient, payloadSerializer, new ObjectPoolFactoryImpl());
        realReporter.start();

        tracer = new ElasticApmTracerBuilder()
            .configurationRegistry(configurationRegistry)
            .reporter(realReporter)
            .buildAndStart();
        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install());
    }

    @AfterClass
    public static void stopElasticsearchContainerAndClient() throws IOException {
        client.indices().delete(new DeleteIndexRequest(INDEX), RequestOptions.DEFAULT);
        container.stop();
        client.close();

        realReporter.flush();
    }

    @Before
    public void startTransaction() {
        tracer.startRootTransaction(null)
            .activate()
            .withName("transaction")
            .withType("request")
            .withResult("success");
    }

    @After
    public void endTransaction() {
        TransactionImpl currentTransaction = tracer.currentTransaction();
        if (currentTransaction != null) {
            currentTransaction.end();
        }
    }

    @Test
    public void testTryToDeleteNonExistingIndex() throws IOException {
        ElasticsearchStatusException ese = null;
        try {
            client.indices().delete(new DeleteIndexRequest(SECOND_INDEX), RequestOptions.DEFAULT);
        } catch (ElasticsearchStatusException e) {
            ese = e;
        }
        assertThat(ese).isNotNull();
        assertThat(ese.status().getStatus()).isEqualTo(404);
    }

    @Test
    public void testCreateAndDeleteIndex() throws IOException {
        // Create an Index
        client.indices().create(new CreateIndexRequest(SECOND_INDEX), RequestOptions.DEFAULT);
        client.indices().delete(new DeleteIndexRequest(SECOND_INDEX), RequestOptions.DEFAULT);
    }

    @Test
    public void testDocumentScenario() throws IOException {
        // Index a document
        IndexResponse ir = client.index(new IndexRequest(INDEX, DOC_TYPE, DOC_ID).source(
            jsonBuilder()
                .startObject()
                .field(FOO, BAR)
                .endObject()
        ).setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE), RequestOptions.DEFAULT);
        assertThat(ir.status().getStatus()).isEqualTo(201);

        SearchRequest searchRequest = new SearchRequest(INDEX);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.query(QueryBuilders.termQuery(FOO, BAR));
        sourceBuilder.from(0);
        sourceBuilder.size(5);
        searchRequest.source(sourceBuilder);
        SearchResponse sr = client.search(searchRequest, RequestOptions.DEFAULT);
        assertThat(sr.getHits().totalHits).isEqualTo(1L);
        assertThat(sr.getHits().getAt(0).getSourceAsMap().get(FOO)).isEqualTo(BAR);

        // Now update and re-search
        Map<String, Object> jsonMap = new HashMap<>();
        jsonMap.put(FOO, BAZ);
        UpdateResponse ur = client.update(new UpdateRequest(INDEX, DOC_TYPE, DOC_ID).doc(jsonMap)
            .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE), RequestOptions.DEFAULT);
        assertThat(ur.status().getStatus()).isEqualTo(200);
        sr = client.search(new SearchRequest(INDEX), RequestOptions.DEFAULT);
        assertThat(sr.getHits().getAt(0).getSourceAsMap().get(FOO)).isEqualTo(BAZ);

        // Finally - delete the document
        DeleteResponse dr = client.delete(new DeleteRequest(INDEX, DOC_TYPE, DOC_ID), RequestOptions.DEFAULT);
        assertThat(dr.status().getStatus()).isEqualTo(200);
    }

    @Test
    public void testScenarioAsBulkRequest() throws IOException {
        client.bulk(new BulkRequest()
                .add(new IndexRequest(INDEX, DOC_TYPE, "2").source(
                    jsonBuilder()
                        .startObject()
                        .field(FOO, BAR)
                        .endObject()
                ))
                .add(new IndexRequest(INDEX, DOC_TYPE, "3").source(
                    jsonBuilder()
                        .startObject()
                        .field(FOO, BAR)
                        .endObject()
                ))
            , RequestOptions.DEFAULT);
    }
}
