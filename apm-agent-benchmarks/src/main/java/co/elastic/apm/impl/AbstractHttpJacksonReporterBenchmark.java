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

import co.elastic.apm.CpuProfiler;
import co.elastic.apm.report.serialize.JacksonPayloadSerializer;
import co.elastic.apm.report.serialize.PayloadSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.afterburner.AfterburnerModule;
import okio.Buffer;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;

public abstract class AbstractHttpJacksonReporterBenchmark extends AbstractHttpReporterBenchmark {

    /**
     * Convenience benchmark run method
     * <p>
     * For more accurate results, execute {@code mvn clean package} and run the benchmark via
     * {@code java -jar apm-agent-benchmarks/target/benchmarks.jar -prof gc}
     */
    public static void main(String[] args) throws RunnerException {
        new Runner(new OptionsBuilder()
            .include(AbstractHttpJacksonReporterBenchmark.class.getSimpleName())
            .addProfiler(GCProfiler.class)
            .addProfiler(CpuProfiler.class)
            .build())
            .run();
    }

    @Setup
    public void setUp() throws Exception {
        super.setUp();
        Buffer buffer = new Buffer();
        getPayloadSerializer().serializePayload(buffer, payload);
        System.out.println("Size of payload in bytes: " + buffer.readByteString().toByteArray().length);
    }

    @Override
    protected PayloadSerializer getPayloadSerializer() {
        ObjectMapper objectMapper = getObjectMapper();
        objectMapper.registerModule(new AfterburnerModule());
        return new JacksonPayloadSerializer(objectMapper);
    }

    protected abstract ObjectMapper getObjectMapper();
}
