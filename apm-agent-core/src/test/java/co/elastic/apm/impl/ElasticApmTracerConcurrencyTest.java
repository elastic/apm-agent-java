/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
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
package co.elastic.apm.impl;

import co.elastic.apm.configuration.CoreConfiguration;
import co.elastic.apm.configuration.SpyConfiguration;
import co.elastic.apm.impl.payload.Payload;
import co.elastic.apm.impl.payload.ProcessFactory;
import co.elastic.apm.impl.payload.ServiceFactory;
import co.elastic.apm.impl.payload.SystemInfo;
import co.elastic.apm.impl.transaction.Span;
import co.elastic.apm.impl.transaction.Transaction;
import co.elastic.apm.report.ApmServerReporter;
import co.elastic.apm.report.IntakeV1ReportingEventHandler;
import co.elastic.apm.report.PayloadSender;
import co.elastic.apm.report.ReporterConfiguration;
import co.elastic.apm.report.processor.ProcessorEventHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stagemonitor.configuration.ConfigurationRegistry;

import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

class ElasticApmTracerConcurrencyTest {

    private static final Logger logger = LoggerFactory.getLogger(ElasticApmTracerConcurrencyTest.class);

    private ExecutorService executorService;
    private ElasticApmTracer tracer;

    @BeforeEach
    void setUp() {
        ConfigurationRegistry config = SpyConfiguration.createSpyConfig();
        CoreConfiguration coreConfiguration = config.getConfig(CoreConfiguration.class);
        ReporterConfiguration reporterConfiguration = config.getConfig(ReporterConfiguration.class);
        tracer = new ElasticApmTracerBuilder().reporter(
            new ApmServerReporter(true, reporterConfiguration,
                coreConfiguration,
                new IntakeV1ReportingEventHandler(
                    new ServiceFactory().createService(coreConfiguration, null, null),
                    ProcessFactory.ForCurrentVM.INSTANCE.getProcessInformation(),
                    SystemInfo.create(),
                    new PayloadSender() {
                        @Override
                        public void sendPayload(Payload payload) {
                            logger.info("Sending payload with {} elements", payload.getPayloadSize());
                            payload.recycle();
                        }

                        @Override
                        public long getReported() {
                            return 0;
                        }

                        @Override
                        public long getDropped() {
                            return 0;
                        }
                    }, reporterConfiguration, new ProcessorEventHandler(Collections.emptyList()))))
            .configurationRegistry(config)
            .build();
        executorService = Executors.newFixedThreadPool(100);
    }

//    @Test
    void testCreateTransactions() throws Exception {
        for (int i = 0; i < 100000; i++) {
            executorService.submit(new Runnable() {
                @Override
                public void run() {
                    Transaction transaction = tracer.startTransaction();
                    transaction.withName("test").withType("test");
                    try {
                        Span span = transaction.createSpan();
                        span.withName("SELECT").withType("db");
                        Thread.sleep(3);
                        span.end();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    transaction.end();
                }
            });
        }
        executorService.shutdown();
        executorService.awaitTermination(60, TimeUnit.SECONDS);
    }
}
