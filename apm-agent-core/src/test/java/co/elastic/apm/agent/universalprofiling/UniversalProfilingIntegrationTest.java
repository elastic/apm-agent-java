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
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.configuration.UniversalProfilingConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.Tracer;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Id;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.objectpool.TestObjectPoolFactory;
import co.elastic.otel.JvmtiAccessImpl;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.stagemonitor.configuration.ConfigurationRegistry;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import static co.elastic.apm.agent.testutils.assertions.Assertions.assertThat;
import static co.elastic.apm.agent.universalprofiling.ProfilerSharedMemoryWriter.TLS_STORAGE_SIZE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class UniversalProfilingIntegrationTest {

    private Tracer tracer;
    private MockReporter reporter;

    private TestObjectPoolFactory poolFactory;

    void setupTracer(Consumer<ConfigurationRegistry> configCustomizer) {
        ConfigurationRegistry config = SpyConfiguration.createSpyConfig();
        configCustomizer.accept(config);
        MockTracer.MockInstrumentationSetup mockInstrumentationSetup = MockTracer.createMockInstrumentationSetup(config);

        tracer = mockInstrumentationSetup.getTracer();
        reporter = mockInstrumentationSetup.getReporter();
        poolFactory = mockInstrumentationSetup.getObjectPoolFactory();
    }

    @AfterEach
    public void cleanupTracer() {
        if (tracer != null) {
            reporter.assertRecycledAfterDecrementingReferences();
            poolFactory.checkAllPooledObjectsHaveBeenRecycled();
            tracer.stop();
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
    }

    @Test
    public void ensureDisabledByDefault() {
        ConfigurationRegistry configRegistry = SpyConfiguration.createSpyConfig();
        UniversalProfilingIntegration universalProfilingIntegration = new UniversalProfilingIntegration();
        ElasticApmTracer mockTracer = MockTracer.create(configRegistry);
        universalProfilingIntegration.start(mockTracer);

        verify(mockTracer, never()).registerSpanListener(any());
        assertThat(universalProfilingIntegration.isActive).isFalse();
    }

    @Nested
    @DisabledOnOs(OS.WINDOWS)
    class SharedMemory {

        @Test
        public void testNestedActivations() {
            setupTracer(conf -> doReturn(true)
                .when(conf.getConfig(UniversalProfilingConfiguration.class)).isEnabled());

            Transaction first = tracer.startRootTransaction(null);
            Transaction second = tracer.startRootTransaction(null);
            Span third = second.createSpan();

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
            assertThat(reporter.getTransactions()).containsExactly(first, second);
            assertThat(reporter.getSpans()).containsExactly(third);
        }


        @ParameterizedTest
        @ValueSource(strings = {"my nameßspace", ""})
        public void testProcessStoragePopulated(String environment) {
            setupTracer(conf -> {
                doReturn(true).when(conf.getConfig(UniversalProfilingConfiguration.class)).isEnabled();
                CoreConfiguration core = conf.getConfig(CoreConfiguration.class);
                doReturn("service Ä 1").when(core).getServiceName();
                doReturn(environment).when(core).getEnvironment();
            });
            ByteBuffer buffer = JvmtiAccessImpl.createProcessProfilingCorrelationBufferAlias(1000);
            buffer.order(ByteOrder.nativeOrder());

            assertThat(buffer.getChar()).describedAs("layout-minor-version").isEqualTo((char) 1);
            assertThat(readUtf8Str(buffer)).isEqualTo("service Ä 1");
            assertThat(readUtf8Str(buffer)).isEqualTo(environment);
            assertThat(readUtf8Str(buffer)).describedAs("socket-path").isEqualTo("");
        }

        private String readUtf8Str(ByteBuffer buffer) {
            int serviceNameLen = buffer.getInt();
            byte[] serviceUtf8 = new byte[serviceNameLen];
            buffer.get(serviceUtf8);
            return new String(serviceUtf8, StandardCharsets.UTF_8);
        }
    }

    static void checkTlsIs(@Nullable AbstractSpan<?> span) {
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

    private static byte[] idToBytes(Id id) {
        byte[] buff = new byte[32];
        int len = id.toBytes(buff, 0);
        byte[] result = new byte[len];
        System.arraycopy(buff, 0, result, 0, len);
        return result;
    }
}
