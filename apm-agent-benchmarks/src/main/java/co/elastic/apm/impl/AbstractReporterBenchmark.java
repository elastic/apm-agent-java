/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 the original author or authors
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

import co.elastic.apm.TransactionUtils;
import co.elastic.apm.impl.payload.Agent;
import co.elastic.apm.impl.payload.Framework;
import co.elastic.apm.impl.payload.Language;
import co.elastic.apm.impl.payload.ProcessInfo;
import co.elastic.apm.impl.payload.RuntimeInfo;
import co.elastic.apm.impl.payload.Service;
import co.elastic.apm.impl.payload.SystemInfo;
import co.elastic.apm.impl.payload.TransactionPayload;
import co.elastic.apm.impl.sampling.ConstantSampler;
import co.elastic.apm.impl.stacktrace.StacktraceFactory;
import co.elastic.apm.impl.transaction.Transaction;
import co.elastic.apm.report.ApmServerReporter;
import co.elastic.apm.report.PayloadSender;
import co.elastic.apm.report.Reporter;
import co.elastic.apm.report.ReporterConfiguration;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.util.Collections;

@State(Scope.Benchmark)
@Warmup(iterations = 10)
@Measurement(iterations = 10)
@Fork(1)
public abstract class AbstractReporterBenchmark {

    protected TransactionPayload payload;
    protected ElasticApmTracer tracer;
    private Reporter reporter;
    private PayloadSender payloadSender;

    @Setup
    public void setUp() throws Exception {
        tracer = ElasticApmTracer.builder().reporter(reporter).stacktraceFactory(StacktraceFactory.Noop.INSTANCE).build();
        // in contrast to production configuration, do not drop transactions if the ring buffer is full
        // instead blocking wait until a slot becomes available
        // this is important because otherwise we would not measure the speed at which events can be handled
        // but rather how fast events get discarded
        payloadSender = getPayloadSender();
        Service service = new Service()
            .withName("java-test")
            .withVersion("1.0")
            .withEnvironment("test")
            .withAgent(new Agent("elastic-apm-java", "1.0.0"))
            .withRuntime(new RuntimeInfo("Java", "9.0.4"))
            .withFramework(new Framework("Servlet API", "3.1"))
            .withLanguage(new Language("Java", "9.0.4"));
        ProcessInfo process = new ProcessInfo("/Library/Java/JavaVirtualMachines/jdk-9.0.4.jdk/Contents/Home/bin/java")
            .withPid(2103)
            .withPpid(403L)
            .withArgv(Collections.singletonList("-javaagent:/path/to/elastic-apm-java.jar"));
        SystemInfo system = new SystemInfo("x86_64", "Felixs-MBP", "Mac OS X");
        ReporterConfiguration reporterConfiguration = new ReporterConfiguration();
        reporter = new ApmServerReporter(tracer.getConfigurationRegistry(), service, process, system, payloadSender, false, reporterConfiguration);
        payload = new TransactionPayload(process, service, system);
        for (int i = 0; i < reporterConfiguration.getMaxQueueSize(); i++) {
            Transaction t = new Transaction();
            t.start(tracer, 0, ConstantSampler.of(true));
            TransactionUtils.fillTransaction(t);
            payload.getTransactions().add(t);
        }
    }


    protected abstract PayloadSender getPayloadSender();

    @TearDown
    public void tearDown() {
        reporter.close();
    }

    @Threads(Threads.MAX)
    @Benchmark
    public void testReport() {
        try (Transaction t = tracer.startTransaction()) {
            TransactionUtils.fillTransaction(t);
        }
    }

    @Benchmark
    @Threads(1)
    public void sendPayload() {
        payloadSender.sendPayload(payload);
    }
}
