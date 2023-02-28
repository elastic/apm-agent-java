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
package co.elastic.apm.agent.profiler;

import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.configuration.converter.TimeDuration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.Span;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.common.util.WildcardMatcher;
import co.elastic.apm.agent.testutils.DisabledOnAppleSilicon;
import co.elastic.apm.agent.tracer.Scope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.stagemonitor.configuration.ConfigurationRegistry;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.doReturn;

// async-profiler doesn't work on Windows
@DisabledOnOs(OS.WINDOWS)
@DisabledOnAppleSilicon
class SamplingProfilerTest {

    private MockReporter reporter;

    @Nullable
    private ElasticApmTracer tracer;
    private SamplingProfiler profiler;
    private ProfilingConfiguration profilingConfig;

    @BeforeEach
    void setup() {
        // avoids any test failure to make other tests to fail
        getProfilerTempFiles().forEach(SamplingProfilerTest::silentDeleteFile);
    }

    @AfterEach
    void tearDown() {
        if (tracer != null) {
            tracer.stop();
        }

        getProfilerTempFiles().forEach(SamplingProfilerTest::silentDeleteFile);
    }

    @Test
    void shouldLazilyCreateTempFilesAndCleanThem() throws Exception {

        List<Path> tempFiles = getProfilerTempFiles();
        assertThat(tempFiles).isEmpty();

        // temporary files should be created on-demand, and properly deleted afterwards
        setupProfiler(false);

        assertThat(profiler.getProfilingSessions())
            .describedAs("profiler should not have any session when disabled")
            .isEqualTo(0);

        assertThat(getProfilerTempFiles())
            .describedAs("should not create a temp file when disabled")
            .isEmpty();

        doReturn(true).when(profilingConfig).isProfilingEnabled();

        awaitProfilerStarted(profiler);

        assertThat(getProfilerTempFiles())
            .describedAs("should have created two temp files")
            .hasSize(2);

        profiler.stop();

        assertThat(getProfilerTempFiles())
            .describedAs("should delete temp files when profiler is stopped")
            .isEmpty();


    }

    private static List<Path> getProfilerTempFiles() {
        Path tempFolder = Paths.get(System.getProperty("java.io.tmpdir"));
        try {
            return Files.list(tempFolder)
                .filter(f -> f.getFileName().toString().startsWith("apm-"))
                .sorted()
                .collect(Collectors.toList());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void shouldNotDeleteProvidedFiles() throws Exception {
        // when an existing file is provided to the profiler, we should not delete it
        // unlike the temporary files that are created by profiler itself

        setupProfiler(true);
        profiler.stop();

        Path tempFile1 = Files.createTempFile("apm-provided", "test.bin");
        Path tempFile2 = Files.createTempFile("apm-provided", "test.jfr");

        SamplingProfiler otherProfiler = new SamplingProfiler(tracer, new FixedNanoClock(), tempFile1.toFile(), tempFile2.toFile());

        otherProfiler.start(tracer);
        awaitProfilerStarted(otherProfiler);
        otherProfiler.stop();

        assertThat(tempFile1).exists();
        assertThat(tempFile2).exists();
    }

    @Test
    void testStartCommand() {
        setupProfiler(true);
        assertThat(profiler.createStartCommand()).isEqualTo("start,jfr,event=wall,cstack=n,interval=5ms,filter,file=null,safemode=0");
        doReturn(false).when(profilingConfig).isProfilingLoggingEnabled();
        assertThat(profiler.createStartCommand()).isEqualTo("start,jfr,event=wall,cstack=n,interval=5ms,filter,file=null,safemode=0,log=none");
        doReturn(TimeDuration.of("10ms")).when(profilingConfig).getSamplingInterval();
        doReturn(14).when(profilingConfig).getAsyncProfilerSafeMode();
        assertThat(profiler.createStartCommand()).isEqualTo("start,jfr,event=wall,cstack=n,interval=10ms,filter,file=null,safemode=14,log=none");
    }

    @Test
    void testProfileTransaction() throws Exception {
        setupProfiler(true);
        awaitProfilerStarted(profiler);

        Transaction transaction = tracer.startRootTransaction(null).withName("transaction");
        try (Scope scope = transaction.activateInScope()) {
            // makes sure that the rest will be captured by another profiling session
            // this tests that restoring which threads to profile works
            Thread.sleep(600);
            aInferred(transaction);
        } finally {
            transaction.end();
        }

        await()
            .pollDelay(10, TimeUnit.MILLISECONDS)
            .timeout(5000, TimeUnit.MILLISECONDS)
            .untilAsserted(() -> assertThat(reporter.getSpans()).hasSize(5));

        Optional<Span> testProfileTransaction = reporter.getSpans().stream().filter(s -> s.getNameAsString().equals("SamplingProfilerTest#testProfileTransaction")).findAny();
        assertThat(testProfileTransaction).isPresent();
        assertThat(testProfileTransaction.get().isChildOf(transaction)).isTrue();

        Optional<Span> inferredSpanA = reporter.getSpans().stream().filter(s -> s.getNameAsString().equals("SamplingProfilerTest#aInferred")).findAny();
        assertThat(inferredSpanA).isPresent();
        assertThat(inferredSpanA.get().isChildOf(testProfileTransaction.get())).isTrue();

        Optional<Span> explicitSpanB = reporter.getSpans().stream().filter(s -> s.getNameAsString().equals("bExplicit")).findAny();
        assertThat(explicitSpanB).isPresent();
        assertThat(explicitSpanB.get().isChildOf(inferredSpanA.get())).isTrue();

        Optional<Span> inferredSpanC = reporter.getSpans().stream().filter(s -> s.getNameAsString().equals("SamplingProfilerTest#cInferred")).findAny();
        assertThat(inferredSpanC).isPresent();
        assertThat(inferredSpanC.get().isChildOf(explicitSpanB.get())).isTrue();

        Optional<Span> inferredSpanD = reporter.getSpans().stream().filter(s -> s.getNameAsString().equals("SamplingProfilerTest#dInferred")).findAny();
        assertThat(inferredSpanD).isPresent();
        assertThat(inferredSpanD.get().isChildOf(inferredSpanC.get())).isTrue();
    }

    @Test
    void testPostProcessingDisabled() throws Exception {
        setupProfiler(true);
        doReturn(false).when(profilingConfig).isPostProcessingEnabled();
        awaitProfilerStarted(profiler);

        Transaction transaction = tracer.startRootTransaction(null).withName("transaction");
        try (Scope scope = transaction.activateInScope()) {
            // makes sure that the rest will be captured by another profiling session
            // this tests that restoring which threads to profile works
            Thread.sleep(600);
            aInferred(transaction);
        } finally {
            transaction.end();
        }

        await()
            .pollDelay(10, TimeUnit.MILLISECONDS)
            .timeout(5000, TimeUnit.MILLISECONDS)
            .untilAsserted(() -> assertThat(reporter.getSpans()).hasSize(1));

        Optional<Span> explicitSpanB = reporter.getSpans().stream().filter(s -> s.getNameAsString().equals("bExplicit")).findAny();
        assertThat(explicitSpanB).isPresent();
        assertThat(explicitSpanB.get().isChildOf(transaction)).isTrue();
    }

    private void aInferred(Transaction transaction) throws Exception {
        Span span = transaction.createSpan().withName("bExplicit").withType("custom");
        try (Scope spanScope = span.activateInScope()) {
            cInferred();
        } finally {
            span.end();
        }
        Thread.sleep(50);
    }

    private void cInferred() throws Exception {
        dInferred();
        Thread.sleep(50);
    }

    private void dInferred() throws Exception {
        Thread.sleep(50);
    }


    private void setupProfiler(boolean enabled) {
        reporter = new MockReporter();
        ConfigurationRegistry config = SpyConfiguration.createSpyConfig();
        profilingConfig = config.getConfig(ProfilingConfiguration.class);

        doReturn(List.of(WildcardMatcher.valueOf(getClass().getName()))).when(profilingConfig).getIncludedClasses();
        doReturn(enabled).when(profilingConfig).isProfilingEnabled();
        doReturn(TimeDuration.of("500ms")).when(profilingConfig).getProfilingDuration();
        doReturn(TimeDuration.of("500ms")).when(profilingConfig).getProfilingInterval();
        doReturn(TimeDuration.of("5ms")).when(profilingConfig).getSamplingInterval();
        tracer = MockTracer.createRealTracer(reporter, config);
        profiler = tracer.getLifecycleListener(ProfilingFactory.class).getProfiler();
    }


    private static void awaitProfilerStarted(SamplingProfiler profiler) {
        // ensure profiler is initialized
        await()
            .pollDelay(10, TimeUnit.MILLISECONDS)
            .timeout(6000, TimeUnit.MILLISECONDS)
            .until(() -> profiler.getProfilingSessions() > 1);
    }

    private static void silentDeleteFile(Path f) {
        try {
            Files.delete(f);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
