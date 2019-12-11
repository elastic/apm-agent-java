/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
 * %%
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
 * #L%
 */
package co.elastic.apm.agent.benchmark;

import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.ElasticApmTracerBuilder;
import io.undertow.Undertow;
import io.undertow.server.handlers.BlockingHandler;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;
import org.stagemonitor.configuration.ConfigurationOptionProvider;
import org.stagemonitor.configuration.ConfigurationRegistry;
import org.stagemonitor.configuration.source.SimpleSource;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.ServiceLoader;
import java.util.concurrent.ExecutionException;

public class AbstractMockApmServerBenchmark extends AbstractBenchmark {
    protected final boolean apmEnabled;
    private final byte[] buffer = new byte[32 * 1024];
    private Undertow server;
    protected ElasticApmTracer tracer;
    private long receivedPayloads = 0;
    private long receivedBytes = 0;

    public AbstractMockApmServerBenchmark(boolean apmEnabled) {
        this.apmEnabled = apmEnabled;
    }

    @Setup
    public void setUp(Blackhole blackhole) throws IOException {
        server = Undertow.builder()
            .addHttpListener(0, "127.0.0.1")
            .setHandler(new BlockingHandler(exchange -> {
                if (!exchange.getRequestPath().equals("/healthcheck")) {
                    receivedPayloads++;
                    exchange.startBlocking();
                    try (InputStream is = exchange.getInputStream()) {
                        for (int n = 0; -1 != n; n = is.read(buffer)) {
                            receivedBytes += n;
                        }
                    }
                    System.getProperties().put("server.received.bytes", receivedBytes);
                    System.getProperties().put("server.received.payloads", receivedPayloads);
                    exchange.setStatusCode(200).endExchange();
                }
            })).build();

        server.start();
        int port = ((InetSocketAddress) server.getListenerInfo().get(0).getAddress()).getPort();
        tracer = new ElasticApmTracerBuilder()
            .configurationRegistry(ConfigurationRegistry.builder()
                .addConfigSource(new SimpleSource()
                    .add(CoreConfiguration.SERVICE_NAME, "benchmark")
                    .add(CoreConfiguration.INSTRUMENT, Boolean.toString(apmEnabled))
                    .add(CoreConfiguration.ACTIVE, Boolean.toString(apmEnabled))
                    .add("api_request_size", "10mb")
                    .add("capture_headers", "false")
                    .add("classes_excluded_from_instrumentation", "java.*,com.sun.*,sun.*")
                    .add("server_urls", "http://localhost:" + port))
                .optionProviders(ServiceLoader.load(ConfigurationOptionProvider.class))
                .build())
            .build();
        ElasticApmAgent.initInstrumentation(tracer, ByteBuddyAgent.install());

    }

    @TearDown
    public void tearDown() throws ExecutionException, InterruptedException {
        Thread.sleep(1000);
        tracer.getReporter().flush().get();
        server.stop();
        System.out.println("Reported: " + tracer.getReporter().getReported());
        System.out.println("Dropped: " + tracer.getReporter().getDropped());
        System.out.println("receivedPayloads = " + receivedPayloads);
        System.out.println("receivedBytes = " + receivedBytes);
    }
}
