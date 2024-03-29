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

import co.elastic.apm.agent.tracer.AbstractLifecycleListener;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.tracer.Tracer;

public class ProfilingFactory extends AbstractLifecycleListener {

    private final SamplingProfiler profiler;
    private final NanoClock nanoClock;

    public ProfilingFactory(ElasticApmTracer tracer) {
        boolean envTest = false;
        // in unit tests, where assertions are enabled, this envTest is true
        assert envTest = true;
        nanoClock = envTest ? new FixedNanoClock() : new SystemNanoClock();
        profiler = new SamplingProfiler(tracer, nanoClock);
    }

    @Override
    public void start(Tracer tracer) throws Exception {
        profiler.start(tracer);
        ElasticApmTracer elasticApmTracer = tracer.require(ElasticApmTracer.class);
        elasticApmTracer.registerSpanListener(new ProfilingActivationListener(elasticApmTracer, profiler));
    }

    @Override
    public void stop() throws Exception {
        profiler.stop();
    }

    public SamplingProfiler getProfiler() {
        return profiler;
    }

    public NanoClock getNanoClock() {
        return nanoClock;
    }
}
