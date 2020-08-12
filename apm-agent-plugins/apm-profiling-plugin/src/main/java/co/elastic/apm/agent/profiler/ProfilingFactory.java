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
package co.elastic.apm.agent.profiler;

import co.elastic.apm.agent.context.AbstractLifecycleListener;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.util.ExecutorUtils;

import java.io.File;
import java.io.IOException;

public class ProfilingFactory extends AbstractLifecycleListener {

    private final SamplingProfiler profiler;
    private final NanoClock nanoClock;
    private final File activationEventsFile;
    private final File jfrFile;

    public ProfilingFactory(ElasticApmTracer tracer) throws IOException {
        boolean envTest = false;
        // in unit tests, where assertions are enabled, this envTest is true
        assert envTest = true;
        nanoClock = envTest ? new FixedNanoClock() : new SystemNanoClock();
        activationEventsFile = File.createTempFile("apm-activation-events-", ".bin");
        jfrFile = File.createTempFile("apm-traces-", ".jfr");
        profiler = new SamplingProfiler(tracer,
            ExecutorUtils.createSingleThreadSchedulingDaemonPool("sampling-profiler"),
            nanoClock,
            activationEventsFile,
            jfrFile);
    }

    @Override
    public void start(ElasticApmTracer tracer) {
        profiler.start(tracer);
        tracer.registerSpanListener(new ProfilingActivationListener(tracer, profiler));
    }

    @Override
    public void stop() throws Exception {
        profiler.stop();
        if (!jfrFile.delete()) {
            jfrFile.deleteOnExit();
        }

        if (!activationEventsFile.delete()) {
            activationEventsFile.deleteOnExit();
        }
    }

    public SamplingProfiler getProfiler() {
        return profiler;
    }

    public NanoClock getNanoClock() {
        return nanoClock;
    }
}
