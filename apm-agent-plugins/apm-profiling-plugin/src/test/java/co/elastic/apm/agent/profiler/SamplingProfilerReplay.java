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
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Can be used in combination with the files created by
 * {@link ProfilingConfiguration#backupDiagnosticFiles} to replay the creation of profiler-inferred spans.
 * This is useful, for example, to troubleshoot why {@link co.elastic.apm.agent.impl.transaction.Span#childIds} are set as expected.
 */
public class SamplingProfilerReplay {

    private static final Logger logger = LoggerFactory.getLogger(SamplingProfilerReplay.class);

    public static void main(String[] args) throws Exception {
        ClassLoader.getSystemClassLoader().setDefaultAssertionStatus(true);
        File activationEventsFile = File.createTempFile("activations", ".dat");
        activationEventsFile.deleteOnExit();
        File jfrFile = File.createTempFile("traces", ".jfr");
        jfrFile.deleteOnExit();
        MockReporter reporter = new MockReporter();
        SamplingProfiler samplingProfiler = new SamplingProfiler(MockTracer.createRealTracer(reporter),
            new SystemNanoClock(),
            activationEventsFile,
            jfrFile);
        Path baseDir = Paths.get(System.getProperty("java.io.tmpdir"), "profiler");
        List<Path> activationFiles = Files.list(baseDir).filter(p -> p.toString().endsWith("activations.dat")).sorted().collect(Collectors.toList());
        List<Path> traceFiles = Files.list(baseDir).filter(p -> p.toString().endsWith("traces.jfr")).sorted().collect(Collectors.toList());
        if (traceFiles.size() != activationFiles.size()) {
            throw new IllegalStateException();
        }
        for (int i = 0; i < activationFiles.size(); i++) {
            logger.info("processing {} {}", activationFiles.get(i), traceFiles.get(i));
            samplingProfiler.copyFromFiles(activationFiles.get(i), traceFiles.get(i));
            samplingProfiler.processTraces();
        }
        logger.info("{}", reporter.getSpans());
    }
}
