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
package co.elastic.apm.benchmark.reporter;

import co.elastic.apm.report.ApmServerHttpPayloadSender;
import co.elastic.apm.report.PayloadSender;
import co.elastic.apm.report.ReporterConfiguration;
import co.elastic.apm.report.serialize.PayloadSerializer;
import io.undertow.Undertow;
import okhttp3.OkHttpClient;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

public abstract class AbstractHttpReporterBenchmark extends AbstractReporterBenchmark {
    private Undertow server;
    private int port;
    private PayloadSerializer payloadSerializer;
    private BlackholeOutputStream blackholeOutputStream;

    @Setup
    public void setUp(Blackhole blackhole) throws Exception {
        super.setUp();
        blackholeOutputStream = new BlackholeOutputStream(blackhole);
        server = Undertow.builder()
            .addHttpListener(0, "127.0.0.1")
            .setHandler(exchange -> exchange.setStatusCode(200).endExchange()).build();
        server.start();
        port = ((InetSocketAddress) server.getListenerInfo().get(0).getAddress()).getPort();
        payloadSerializer = getPayloadSerializer();
    }

    @Override
    protected PayloadSender getPayloadSender() {
        return new ApmServerHttpPayloadSender(new OkHttpClient(), getPayloadSerializer(), new ReporterConfiguration() {
            @Override
            public String getServerUrl() {
                return "http://localhost:" + port;
            }
        });
    }

    protected abstract PayloadSerializer getPayloadSerializer();

    @TearDown
    public void tearDown() {
        super.tearDown();
        server.stop();
    }

    @Benchmark
    @Threads(1)
    public void testSerialization() throws IOException {
        payloadSerializer.serializePayload(blackholeOutputStream, payload);
    }

    private static class BlackholeOutputStream extends OutputStream {

        private final Blackhole blackhole;

        private BlackholeOutputStream(Blackhole blackhole) {
            this.blackhole = blackhole;
        }

        @Override
        public void write(int b) {
            blackhole.consume(b);
        }

    }

}
