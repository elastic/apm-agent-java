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
import okio.Buffer;
import okio.BufferedSink;
import okio.ByteString;
import okio.Source;
import okio.Timeout;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;

public abstract class AbstractHttpReporterBenchmark extends AbstractReporterBenchmark {
    private Undertow server;
    private int port;
    private PayloadSerializer payloadSerializer;
    private NoopBufferedSink noopBufferedSink;

    @Setup
    public void setUp(Blackhole blackhole) throws Exception {
        super.setUp();
        noopBufferedSink = new NoopBufferedSink(blackhole);
        server = Undertow.builder()
            .addHttpListener(0, "127.0.0.1")
            .setHandler(exchange -> exchange.setStatusCode(200).endExchange()).build();
        server.start();
        port = ((InetSocketAddress) server.getListenerInfo().get(0).getAddress()).getPort();
        payloadSerializer = getPayloadSerializer();
    }

    @Override
    protected PayloadSender getPayloadSender() {
        return new ApmServerHttpPayloadSender(new OkHttpClient(), payloadSerializer, new ReporterConfiguration() {
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
        payloadSerializer.serializePayload(noopBufferedSink, payload);
    }

    @SuppressWarnings("ConstantConditions")
    private static class NoopBufferedSink implements BufferedSink {

        private final Blackhole blackhole;

        private NoopBufferedSink(Blackhole blackhole) {
            this.blackhole = blackhole;
        }

        @Override
        public Buffer buffer() {
            return null;
        }

        @Override
        public BufferedSink write(ByteString byteString) throws IOException {
            blackhole.consume(byteString);
            return this;
        }

        @Override
        public BufferedSink write(byte[] source) throws IOException {
            blackhole.consume(source);
            return this;
        }

        @Override
        public BufferedSink write(byte[] source, int offset, int byteCount) throws IOException {
            blackhole.consume(source);
            return this;
        }

        @Override
        public long writeAll(Source source) throws IOException {
            blackhole.consume(source);
            return 0;
        }

        @Override
        public BufferedSink write(Source source, long byteCount) throws IOException {
            blackhole.consume(source);
            return this;
        }

        @Override
        public BufferedSink writeUtf8(String string) throws IOException {
            blackhole.consume(string);
            return this;
        }

        @Override
        public BufferedSink writeUtf8(String string, int beginIndex, int endIndex) throws IOException {
            blackhole.consume(string);
            return this;
        }

        @Override
        public BufferedSink writeUtf8CodePoint(int codePoint) throws IOException {
            blackhole.consume(codePoint);
            return this;
        }

        @Override
        public BufferedSink writeString(String string, Charset charset) throws IOException {
            blackhole.consume(string);
            return this;
        }

        @Override
        public BufferedSink writeString(String string, int beginIndex, int endIndex, Charset charset) throws IOException {
            blackhole.consume(string);
            return this;
        }

        @Override
        public BufferedSink writeByte(int b) throws IOException {
            blackhole.consume(b);
            return this;
        }

        @Override
        public BufferedSink writeShort(int s) throws IOException {
            blackhole.consume(s);
            return this;
        }

        @Override
        public BufferedSink writeShortLe(int s) throws IOException {
            blackhole.consume(s);
            return this;
        }

        @Override
        public BufferedSink writeInt(int i) throws IOException {
            blackhole.consume(i);
            return this;
        }

        @Override
        public BufferedSink writeIntLe(int i) throws IOException {
            blackhole.consume(i);
            return this;
        }

        @Override
        public BufferedSink writeLong(long v) throws IOException {
            blackhole.consume(v);
            return this;
        }

        @Override
        public BufferedSink writeLongLe(long v) throws IOException {
            blackhole.consume(v);
            return this;
        }

        @Override
        public BufferedSink writeDecimalLong(long v) throws IOException {
            blackhole.consume(v);
            return this;
        }

        @Override
        public BufferedSink writeHexadecimalUnsignedLong(long v) throws IOException {
            blackhole.consume(v);
            return this;
        }

        @Override
        public void flush() throws IOException {

        }

        @Override
        public BufferedSink emit() throws IOException {
            return this;
        }

        @Override
        public BufferedSink emitCompleteSegments() throws IOException {
            return this;
        }

        @Override
        public OutputStream outputStream() {
            return new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                    blackhole.consume(b);
                }

                @Override
                public void write(byte[] b) throws IOException {
                    blackhole.consume(b);
                }

                @Override
                public void write(byte[] b, int off, int len) throws IOException {
                    blackhole.consume(b);
                }
            };
        }

        @Override
        public void write(Buffer source, long byteCount) throws IOException {
            blackhole.consume(source);
        }

        @Override
        public Timeout timeout() {
            return null;
        }

        @Override
        public void close() throws IOException {

        }
    }
}
