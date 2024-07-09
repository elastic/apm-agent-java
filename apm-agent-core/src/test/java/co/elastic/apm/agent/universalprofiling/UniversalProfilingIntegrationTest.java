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
package co.elastic.apm.agent.universalprofiling;

import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.configuration.CoreConfigurationImpl;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.configuration.UniversalProfilingConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.sampling.ConstantSampler;
import co.elastic.apm.agent.impl.transaction.AbstractSpanImpl;
import co.elastic.apm.agent.impl.transaction.IdImpl;
import co.elastic.apm.agent.impl.transaction.SpanImpl;
import co.elastic.apm.agent.impl.transaction.TransactionImpl;
import co.elastic.apm.agent.objectpool.TestObjectPoolFactory;
import co.elastic.otel.JvmtiAccessImpl;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.stagemonitor.configuration.ConfigurationRegistry;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static co.elastic.apm.agent.testutils.assertions.Assertions.assertThat;
import static co.elastic.apm.agent.universalprofiling.ProfilerSharedMemoryWriter.TLS_STORAGE_SIZE;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class UniversalProfilingIntegrationTest {

    private ElasticApmTracer tracer;
    private MockReporter reporter;

    private TestObjectPoolFactory poolFactory;

    @TempDir
    Path tempDir;

    void setupTracer() {
        setupTracer(config -> {
            UniversalProfilingConfiguration conf = config.getConfig(UniversalProfilingConfiguration.class);
            doReturn(true).when(conf).isEnabled();
            doReturn(tempDir.toAbsolutePath().toString()).when(conf).getSocketDir();
        });
    }
    void setupTracer(Consumer<ConfigurationRegistry> configCustomizer) {
        ConfigurationRegistry config = SpyConfiguration.createSpyConfig();
        configCustomizer.accept(config);
        MockTracer.MockInstrumentationSetup mockInstrumentationSetup = MockTracer.createMockInstrumentationSetup(config, false);

        tracer = mockInstrumentationSetup.getTracer();
        reporter = mockInstrumentationSetup.getReporter();
        poolFactory = mockInstrumentationSetup.getObjectPoolFactory();
    }

    @AfterEach
    public void cleanupTracer() {
        if (tracer != null) {
            if (tracer.isRunning()) {
                tracer.stop();
            }
            reporter.assertRecycledAfterDecrementingReferences();
            poolFactory.checkAllPooledObjectsHaveBeenRecycled();
            tracer = null;
        }
    }

    @EnabledOnOs(OS.WINDOWS)
    @Test
    public void ensureDisabledOnWindows() {
        ConfigurationRegistry configRegistry = SpyConfiguration.createSpyConfig();
        UniversalProfilingConfiguration config = configRegistry.getConfig(UniversalProfilingConfiguration.class);

        doReturn(true).when(config).isEnabled();

        UniversalProfilingIntegration universalProfilingIntegration = new UniversalProfilingIntegration();
        ElasticApmTracer mockTracer = MockTracer.create(configRegistry);
        universalProfilingIntegration.start(mockTracer);

        verify(mockTracer, never()).registerSpanListener(any());
        assertThat(universalProfilingIntegration.isActive).isFalse();
        assertThat(universalProfilingIntegration.socketPath).isNull();
    }

    @Test
    public void ensureDisabledByDefault() {
        ConfigurationRegistry configRegistry = SpyConfiguration.createSpyConfig();
        UniversalProfilingIntegration universalProfilingIntegration = new UniversalProfilingIntegration();
        ElasticApmTracer mockTracer = MockTracer.create(configRegistry);
        universalProfilingIntegration.start(mockTracer);

        verify(mockTracer, never()).registerSpanListener(any());
        assertThat(universalProfilingIntegration.isActive).isFalse();
        assertThat(universalProfilingIntegration.socketPath).isNull();
    }

    @Nested
    @DisabledOnOs(OS.WINDOWS)
    class SharedMemory {

        @Test
        public void testNestedActivations() {
            setupTracer();

            TransactionImpl first = tracer.startRootTransaction(null);
            TransactionImpl second = tracer.startRootTransaction(null);
            SpanImpl third = second.createSpan();

            checkTlsIs(null);

            first.activate();
            {
                checkTlsIs(first);

                //nested activation
                first.activate();
                {
                    checkTlsIs(first);
                }
                first.deactivate();
                second.activate();
                {
                    checkTlsIs(second);
                    third.activate();
                    {
                        checkTlsIs(third);

                        first.activate();
                        {
                            checkTlsIs(first);
                        }
                        first.deactivate();
                    }
                    third.deactivate();
                    checkTlsIs(second);
                }
                second.deactivate();
                checkTlsIs(first);
            }
            first.deactivate();
            checkTlsIs(null);

            first.end();
            second.end();
            third.end();
        }


        @ParameterizedTest
        @ValueSource(strings = {"my nameßspace", ""})
        public void testProcessStoragePopulated(String environment) {
            setupTracer(conf -> {
                UniversalProfilingConfiguration profConfig = conf.getConfig(UniversalProfilingConfiguration.class);
                doReturn(true).when(profConfig).isEnabled();
                doReturn(tempDir.toAbsolutePath().toString()).when(profConfig).getSocketDir();
                CoreConfigurationImpl core = conf.getConfig(CoreConfigurationImpl.class);
                doReturn("service Ä 1").when(core).getServiceName();
                doReturn(environment).when(core).getEnvironment();
            });
            ByteBuffer buffer = JvmtiAccessImpl.createProcessProfilingCorrelationBufferAlias(1000);
            buffer.order(ByteOrder.nativeOrder());

            assertThat(buffer.getChar()).describedAs("layout-minor-version").isEqualTo((char) 1);
            assertThat(readUtf8Str(buffer)).isEqualTo("service Ä 1");
            assertThat(readUtf8Str(buffer)).isEqualTo(environment);
            assertThat(readUtf8Str(buffer)).describedAs("socket-path")
                    .startsWith(tempDir.toAbsolutePath().toString() + "/essock");
        }

        private String readUtf8Str(ByteBuffer buffer) {
            int serviceNameLen = buffer.getInt();
            byte[] serviceUtf8 = new byte[serviceNameLen];
            buffer.get(serviceUtf8);
            return new String(serviceUtf8, StandardCharsets.UTF_8);
        }
    }

    static void checkTlsIs(@Nullable AbstractSpanImpl<?> span) {
        ByteBuffer tls = JvmtiAccessImpl.createThreadProfilingCorrelationBufferAlias(TLS_STORAGE_SIZE);
        if (tls != null) {
            tls.order(ByteOrder.nativeOrder());
            Assertions.assertThat(tls.getChar(0)).describedAs("layout-minor-version").isEqualTo((char) 1);
            Assertions.assertThat(tls.get(2)).describedAs("valid byte").isEqualTo((byte) 1);
        }

        if (span != null) {
            assertThat(tls).isNotNull();
            Assertions.assertThat(tls.get(3)).describedAs("trace-present-flag").isEqualTo((byte) 1);
            Assertions.assertThat(tls.get(4)).describedAs("trace-flags").isEqualTo(span.getTraceContext().getFlags());

            byte[] traceId = new byte[16];
            byte[] spanId = new byte[8];
            byte[] localRootSpanId = new byte[8];
            tls.position(5);
            tls.get(traceId);
            assertThat(traceId).containsExactly(idToBytes(span.getTraceContext().getTraceId()));
            tls.position(21);
            tls.get(spanId);
            assertThat(spanId).containsExactly(idToBytes(span.getTraceContext().getId()));
            tls.position(29);
            tls.get(localRootSpanId);
            assertThat(localRootSpanId).containsExactly(idToBytes(span.getParentTransaction().getTraceContext().getId()));
        } else if (tls != null) {
            Assertions.assertThat(tls.get(3)).describedAs("trace-present-flag").isEqualTo((byte) 0);
        }
    }

    @DisabledOnOs(OS.WINDOWS)
    @Nested
    class SpanCorrelation {


        @Test
        void checkCorrelationFunctional() {
            AtomicLong clockMs = new AtomicLong(0L);
            setupTracer();
            UniversalProfilingIntegration profilingIntegration = tracer.getProfilingIntegration();
            profilingIntegration.correlator.nanoClock = () -> clockMs.get() * 1_000_000L;

            sendProfilerRegistrationMsg(1, "hostid");

            TransactionImpl tx1 = tracer.startRootTransaction(null);
            TransactionImpl tx2 = tracer.startRootTransaction(null);

            IdImpl st1 = randomStackTraceId(1);
            sendSampleMsg(tx1, st1, 1);

            // Send some garbage which should not affect our processing
            JvmtiAccessImpl.sendToProfilerReturnChannelSocket0(new byte[]{1, 2, 3});

            IdImpl st2 = randomStackTraceId(2);
            sendSampleMsg(tx2, st2, 2);

            // ensure that the messages are processed now
            profilingIntegration.periodicTimer();

            tx1.end();
            tx2.end();

            // ensure that spans are not sent, their delay has not yet elapsed
            assertThat(reporter.getTransactions()).isEmpty();

            IdImpl st3 = randomStackTraceId(3);
            sendSampleMsg(tx2, st2, 1);
            sendSampleMsg(tx1, st3, 2);
            sendSampleMsg(tx2, st3, 1);

            clockMs.set(1L + UniversalProfilingIntegration.POLL_FREQUENCY_MS);
            // now the background thread should consume those messages and flush the spans
            await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(
                    () -> {

                        assertThat(reporter.getTransactions())
                            .hasSize(2)
                            .containsExactlyInAnyOrder(tx1, tx2);

                        assertThat(tx1.getProfilingCorrelationStackTraceIds())
                            .containsExactlyInAnyOrder(st1, st3, st3);

                        assertThat(tx2.getProfilingCorrelationStackTraceIds())
                            .containsExactlyInAnyOrder(st2, st2, st2, st3);
                    });
        }

        @Test
        void unsampledTransactionsNotCorrelated() {
            setupTracer();
            UniversalProfilingIntegration profilingIntegration = tracer.getProfilingIntegration();
            profilingIntegration.correlator.nanoClock = () -> 0L;
            doReturn(true).when(tracer.getApmServerClient()).supportsKeepingUnsampledTransaction();

            sendProfilerRegistrationMsg(1, "hostid");

            TransactionImpl tx = tracer.startRootTransaction(ConstantSampler.of(false), 0L, null);
            assertThat(tx.isSampled()).isFalse();

            // Still send a stacktrace to make sure it is actually ignored
            sendSampleMsg(tx, randomStackTraceId(1), 1);

            // ensure that the messages are processed now
            profilingIntegration.periodicTimer();
            tx.end();

            assertThat(reporter.getTransactions()).containsExactly(tx);
            assertThat(tx.getProfilingCorrelationStackTraceIds()).isEmpty();
        }

        @Test
        void shutdownFlushesBufferedSpans() {
            IdImpl st1 = randomStackTraceId(1);

            setupTracer();
            UniversalProfilingIntegration profilingIntegration = tracer.getProfilingIntegration();
            profilingIntegration.correlator.nanoClock = () -> 0L;

            sendProfilerRegistrationMsg(1, "hostid");
            profilingIntegration.periodicTimer();

            TransactionImpl tx = tracer.startRootTransaction(null);
            tx.end();

            profilingIntegration.periodicTimer();
            assertThat(reporter.getTransactions()).isEmpty();

            sendSampleMsg(tx, st1, 1);
            tracer.stop();

            assertThat(reporter.getTransactions()).containsExactly(tx);
            assertThat(tx.getProfilingCorrelationStackTraceIds()).containsExactly(st1);
        }

        @Test
        void bufferCapacityExceeded() {
            setupTracer(conf -> {
                UniversalProfilingConfiguration profConfig = conf.getConfig(UniversalProfilingConfiguration.class);
                doReturn(true).when(profConfig).isEnabled();
                doReturn(tempDir.toAbsolutePath().toString()).when(profConfig).getSocketDir();
                doReturn(2).when(profConfig).getBufferSize();
            });
            UniversalProfilingIntegration profilingIntegration = tracer.getProfilingIntegration();
            profilingIntegration.correlator.nanoClock = () -> 0L;


            sendProfilerRegistrationMsg(1, "hostid");
            profilingIntegration.periodicTimer();
            TransactionImpl tx1 = tracer.startRootTransaction(null);
            tx1.end();
            TransactionImpl tx2 = tracer.startRootTransaction(null);
            tx2.end();
            //the actual buffer capacity is 2 + 1 because the peeked transaction is stored outside of the buffer
            profilingIntegration.periodicTimer();
            TransactionImpl tx3 = tracer.startRootTransaction(null);
            tx3.end();
            // now the buffer should be full, transaction 4 should be sent immediately
            TransactionImpl tx4 = tracer.startRootTransaction(null);
            tx4.end();

            Assertions.assertThat(reporter.getTransactions()).containsExactly(tx4);
        }

        @Test
        void badSocketPath() throws Exception {
            Path notADir = tempDir.resolve("not_a_dir");
            Files.createFile(notADir);
            String absPath = notADir.toAbsolutePath().toString();

            ConfigurationRegistry configRegistry = SpyConfiguration.createSpyConfig();
            UniversalProfilingConfiguration config = configRegistry.getConfig(UniversalProfilingConfiguration.class);

            doReturn(true).when(config).isEnabled();
            doReturn(absPath).when(config).getSocketDir();

            UniversalProfilingIntegration universalProfilingIntegration = new UniversalProfilingIntegration();
            ElasticApmTracer mockTracer = MockTracer.create(configRegistry);

            universalProfilingIntegration.start(mockTracer);

            verify(mockTracer, never()).registerSpanListener(any());
            assertThat(universalProfilingIntegration.isActive).isFalse();
            assertThat(universalProfilingIntegration.socketPath).isNull();
        }

        @Test
        void socketParentDirCreated() throws Exception {
            Path subDirs = tempDir.resolve("create/me");
            String absolute = subDirs.toAbsolutePath().toString();

            ConfigurationRegistry configRegistry = SpyConfiguration.createSpyConfig();
            UniversalProfilingConfiguration config = configRegistry.getConfig(UniversalProfilingConfiguration.class);

            doReturn(true).when(config).isEnabled();
            doReturn(absolute).when(config).getSocketDir();

            UniversalProfilingIntegration universalProfilingIntegration = new UniversalProfilingIntegration();
            ElasticApmTracer mockTracer = MockTracer.create(configRegistry);

            universalProfilingIntegration.start(mockTracer);
            try {
                assertThat(Paths.get(universalProfilingIntegration.socketPath)).exists();
                assertThat(universalProfilingIntegration.socketPath).startsWith(absolute + "/");
            } finally {
                universalProfilingIntegration.stop();
            }
        }


        private IdImpl randomStackTraceId(int seed) {
            byte[] id = new byte[16];
            new Random(seed).nextBytes(id);
            IdImpl idObj = IdImpl.new128BitId();
            idObj.fromBytes(id, 0);
            return idObj;
        }

        void sendSampleMsg(TransactionImpl transaction, IdImpl stackTraceId, int count) {
            byte[] traceId = idToBytes(transaction.getTraceContext().getTraceId());
            byte[] transactionId = idToBytes(transaction.getTraceContext().getId());

            ByteBuffer message = ByteBuffer.allocate(46);
            message.order(ByteOrder.nativeOrder());
            message.putShort((short) 1); // message-type = correlation message
            message.putShort((short) 1); // message-version
            message.put(traceId);
            message.put(transactionId);
            message.put(idToBytes(stackTraceId));
            message.putShort((short) count);

            JvmtiAccessImpl.sendToProfilerReturnChannelSocket0(message.array());
        }

        void sendProfilerRegistrationMsg(int sampleDelayMillis, String hostId) {
            byte[] hostIdUtf8 = hostId.getBytes(StandardCharsets.UTF_8);

            ByteBuffer message = ByteBuffer.allocate(12 + hostIdUtf8.length);
            message.order(ByteOrder.nativeOrder());
            message.putShort((short) 2); // message-type = registration message
            message.putShort((short) 1); // message-version
            message.putInt(sampleDelayMillis);
            message.putInt(hostIdUtf8.length);
            message.put(hostIdUtf8);

            JvmtiAccessImpl.sendToProfilerReturnChannelSocket0(message.array());
        }
    }

    private static byte[] idToBytes(IdImpl id) {
        byte[] buff = new byte[32];
        int len = id.toBytes(buff, 0);
        byte[] result = new byte[len];
        System.arraycopy(buff, 0, result, 0, len);
        return result;
    }
}
