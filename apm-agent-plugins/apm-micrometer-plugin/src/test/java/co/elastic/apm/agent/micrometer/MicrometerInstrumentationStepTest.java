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
package co.elastic.apm.agent.micrometer;

import co.elastic.apm.agent.MockReporter;
import co.elastic.apm.agent.MockTracer;
import co.elastic.apm.agent.bci.ElasticApmAgent;
import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.report.ReporterConfigurationImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.simple.CountingMode;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.stagemonitor.configuration.ConfigurationRegistry;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

// Tests will fail about 1% of the time because occasionally an interval metric
// is dropped (due to the timing of micrometer updates vs our sampling -
// we can't sample more often because micrometer transparently provides the
// previous value, so we'd be sending extra - erroneous - data if we did)
public class MicrometerInstrumentationStepTest {

    private MockReporter reporter;
    private ConfigurationRegistry config;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FooBar foobar = new FooBar(0,0);

    @Before
    public void setUp() {
        config = SpyConfiguration.createSpyConfig();
        doReturn(30_000L).when(config.getConfig(ReporterConfigurationImpl.class)).getMetricsIntervalMs();
        reporter = new MockReporter();
    }

    @After
    public void tearDown() {
        ElasticApmAgent.reset();
    }

    @Test
    public void testStepVsCumulativeOnce() {
        final FooBar result = new FooBar(0,1);
        testStepVsCumulative(result, false);
    }

    @Test
    public void testStepVsCumulativeMultiple() {
        final FooBar result = new FooBar(0,21);
        testStepVsCumulative(result, true);
    }

    @Test
    public void testStepVsCumulativeMultipleSameInterval() {
        doReturn(1_000L).when(config.getConfig(ReporterConfigurationImpl.class)).getMetricsIntervalMs();
        final FooBar result = new FooBar(21,21);
        testStepVsCumulative(result, true);
    }

    public void testStepVsCumulative(final FooBar result, boolean startThread) {
        ElasticApmAgent.initInstrumentation(MockTracer.createRealTracer(reporter, config), ByteBuddyAgent.install());
        final SimpleMeterRegistry registryOneSecondStep = new SimpleMeterRegistry(new SimpleConfig() {
            @Override
            public CountingMode mode() {
                return CountingMode.STEP;
            }

            @Override
            public Duration step() {
                return Duration.ofSeconds(1);
            }

            @Override
            public String get(String key) {
                return null;
            }
        }, Clock.SYSTEM);
        final SimpleMeterRegistry registryOneSecondCumulative = new SimpleMeterRegistry(new SimpleConfig() {

            @Override
            public CountingMode mode() {
                return CountingMode.CUMULATIVE;
            }

            @Override
            public Duration step() {
                return Duration.ofSeconds(1);
            }

            @Override
            public String get(String key) { return null; }
        }, Clock.SYSTEM);
        registryOneSecondCumulative.counter("foo").increment();
        registryOneSecondStep.counter("bar").increment();

        if (startThread) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < 20; i++) {
                        registryOneSecondCumulative.counter("foo").increment();
                        registryOneSecondStep.counter("bar").increment();
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                        }
                    }
                }
            }).start();
        }
        reporter.awaitUntilAsserted(20000, () ->
            assertThat(countFooBars()).isEqualTo(result));
    }


    private FooBar countFooBars() {
        for (JsonNode metricSet : getMetricSets()) {
            JsonNode metricsetNode = metricSet.get("metricset");
            if (metricsetNode != null) {
                JsonNode samples = metricsetNode.get("samples");
                if (samples != null) {
                    JsonNode bar = samples.get("bar");
                    if (bar != null) {
                        foobar.barCount += bar.get("value").asInt();
                    }
                    JsonNode foo = samples.get("foo");
                    if (foo != null) {
                        foobar.fooCount = foo.get("value").asInt();
                    }
                }
            }
        }
        return foobar;
    }

    private List<JsonNode> getMetricSets() {
        List<JsonNode> metricSets = reporter.getBytes()
            .stream()
            .map(k -> new String(k, StandardCharsets.UTF_8))
            .flatMap(s -> Arrays.stream(s.split("\n")))
            .map(this::deserialize)
            .collect(Collectors.toList());
        reporter.reset();
        return metricSets;
    }

    private JsonNode deserialize(String json) {
        System.out.println(json);
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    class FooBar {
        private int fooCount = 0;
        private int barCount = 0;

        public FooBar(int fooCount, int barCount) {
            this.fooCount = fooCount;
            this.barCount = barCount;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FooBar fooBar = (FooBar) o;
            return fooCount == fooBar.fooCount && barCount == fooBar.barCount;
        }

        @Override
        public int hashCode() {
            return Objects.hash(fooCount, barCount);
        }

        @Override
        public String toString() {
            return "FooBar{" +
                "fooCount=" + fooCount +
                ", barCount=" + barCount +
                '}';
        }
    }
}
