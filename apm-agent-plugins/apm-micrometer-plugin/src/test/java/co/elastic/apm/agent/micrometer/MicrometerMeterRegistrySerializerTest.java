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

import co.elastic.apm.agent.configuration.MetricsConfigurationImpl;
import com.dslplatform.json.JsonWriter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class MicrometerMeterRegistrySerializerTest {
    static MicrometerMeterRegistrySerializer serializer;

    @BeforeAll
    static void setup() {
        MetricsConfigurationImpl config = mock(MetricsConfigurationImpl.class);
        serializer = new MicrometerMeterRegistrySerializer(config);
    }

    @Test
    void serializeEmpty() {
        List<JsonWriter> serialized = serializer.serialize(Collections.emptyMap(), 0);
        assertThat(serialized).isEmpty();
    }

    @Test
    void serializeTimer() {
        serializeOneMeter(new TestTimer());
    }

    @Test
    void serializeFunctionTimer() {
        serializeOneMeter(new TestFunctionTimer());
    }

    @Test
    void serializeLongTaskTimer() {
        serializeOneMeter(new TestLongTaskTimer());
    }

    @Test
    void serializeDistributionSummary() {
        serializeOneMeter(new TestSummary());
    }

    @Test
    void serializeDistributionSummaryWithNoValues() {
        serializeOneMeter(new TestSummary(false));
    }

    @Test
    void serializeGauge() {
        serializeOneMeter(new TestGauge());
    }

    @Test
    void serializeCounter() {
        serializeOneMeter(new TestCounter());
    }

    @Test
    void serializeFunctionCounter() {
        serializeOneMeter(new TestFunctionCounter());
    }

    void serializeOneMeter(TestMeter testMeter) {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        testMeter.addToMeterRegistry(registry);
        testMeter.populateValues();
        List<JsonWriter> serialized = serializer.serialize(Map.of(testMeter.meter().getId(), testMeter.meter()), 0);
        assertThat(serialized.size()).isEqualTo(1);
        JsonNode jsonNode = readJsonString(serialized.get(0).toString());
        testMeter.checkSerialization(jsonNode);
    }

    @Test
    void serializeSummaryAndCounter() {
        serializeTwoMeters(new TestCounter(), new TestSummary());
    }

    void serializeTwoMeters(TestMeter testMeter1, TestMeter testMeter2) {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        testMeter1.addToMeterRegistry(registry);
        testMeter2.addToMeterRegistry(registry);
        testMeter1.populateValues();
        testMeter2.populateValues();
        List<JsonWriter> serialized = serializer.serialize(Map.of(
            testMeter1.meter().getId(), testMeter1.meter(),
            testMeter2.meter().getId(), testMeter2.meter()
        ), 0);
        assertThat(serialized.size()).isLessThan(3);
        assertThat(serialized.size()).isGreaterThan(0);
        if (serialized.size() == 1) {
            JsonNode jsonNode = readJsonString(serialized.get(0).toString());
            testMeter1.checkSerialization(jsonNode);
            testMeter2.checkSerialization(jsonNode);
        } else if(serialized.size() == 2) {
            JsonNode jsonNode1 = readJsonString(serialized.get(0).toString());
            JsonNode jsonNode2 = readJsonString(serialized.get(1).toString());
            if (testMeter1.containsValues(jsonNode1)) {
                testMeter1.checkSerialization(jsonNode1);
                testMeter2.checkSerialization(jsonNode2);
            } else {
                testMeter1.checkSerialization(jsonNode2);
                testMeter2.checkSerialization(jsonNode1);
            }
        }
    }

    private static JsonNode readJsonString(String jsonString) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode json = objectMapper.readTree(jsonString);

            // pretty print JSON in standard output for easier test debug
            System.out.println(json.toPrettyString());

            return json;
        } catch (JsonProcessingException e) {
            // any invalid JSON will require debugging the raw string
            throw new IllegalArgumentException("invalid JSON = " + jsonString);
        }
    }

    static abstract class TestMeter {
        abstract void addToMeterRegistry(MeterRegistry registry);
        abstract void populateValues();
        abstract void checkSerialization(JsonNode jsonNode);
        abstract String meternameExtension();

        protected Meter meter;

        public Meter meter() {
            return meter;
        }

        public String metername() {
            return "meter"+getClass().getSimpleName();
        }

        public String[] path() {
            return path(meternameExtension());
        }

        public String[] path(String extension) {
            return new String[]{"metricset", "samples", metername()+extension, "value"};
        }

        public boolean containsValues(JsonNode jsonNode) {
            return containsPathNode(jsonNode, path());
        }

        private static boolean containsPathNode(JsonNode jsonNode, String[] path) {
            if (jsonNode == null) {
                return false;
            }
            for (String element: path) {
                jsonNode = jsonNode.get(element);
                if (jsonNode == null) {
                    return false;
                }
            }
            return true;
        }

        protected static void checkPathHasValue(JsonNode jsonNode, String[] path, int value) {
            jsonNode = getPathNode(jsonNode, path);
            assertThat(jsonNode.asInt()).isEqualTo(value);
        }

        protected static JsonNode getPathNode(JsonNode jsonNode, String[] path) {
            return getPathNode(jsonNode, path, false);
        }
        protected static JsonNode getPathNode(JsonNode jsonNode, String[] path, boolean isNull) {
            assertThat(jsonNode).isNotNull();
            for (int i = 0; i < path.length-1; i++) {
                jsonNode = jsonNode.get(path[i]);
                assertThat(jsonNode).isNotNull();
            }
            jsonNode = jsonNode.get(path[path.length-1]);
            if (isNull) {
                assertThat(jsonNode).isNull();
            } else {
                assertThat(jsonNode).isNotNull();
            }
            return jsonNode;
        }

    }

    static class TestCounter extends TestMeter {

        @Override
        String meternameExtension() {
            return "";
        }

        @Override
        public void addToMeterRegistry(MeterRegistry registry) {
            meter = Counter.builder(metername()).register(registry);
        }

        @Override
        public void populateValues() {
            ((Counter) meter).increment(42);
        }

        @Override
        public void checkSerialization(JsonNode jsonNode) {
            checkPathHasValue(jsonNode, path(), 42);
        }

    }

    static class TestFunctionCounter extends TestMeter {
        long count;

        @Override
        String meternameExtension() {
            return "";
        }

        @Override
        public void addToMeterRegistry(MeterRegistry registry) {
            meter = FunctionCounter.builder(metername(), this, (o)->count).register(registry);
        }

        @Override
        public void populateValues() {
            count = 48;
        }

        @Override
        public void checkSerialization(JsonNode jsonNode) {
            checkPathHasValue(jsonNode, path(), 48);
        }

    }

    static class TestSummary extends TestMeter {
        private final boolean setSLOs;
        int sum = 0;
        int count = 0;
        int under5Count = 0;
        int from5To50Count = 0;
        int from50to95Count = 0;
        int[] values = new int[]{22, 55, 66, 98};

        public TestSummary () {
            this(true);
        }
        public TestSummary (boolean setSLOs) {
            super();
            this.setSLOs = setSLOs;
        }
        @Override
        String meternameExtension() {
            return ".count";
        }

        @Override
        public void addToMeterRegistry(MeterRegistry registry) {
            if (setSLOs) {
                meter = DistributionSummary
                    .builder(metername())
                    .distributionStatisticBufferLength(20)
                    .serviceLevelObjectives(5, 50, 95)
                    .publishPercentileHistogram()
                    .register(registry);
            } else {
                meter = DistributionSummary
                    .builder(metername())
                    .distributionStatisticBufferLength(20)
                    .publishPercentileHistogram()
                    .register(registry);
            }
        }

        @Override
        public void populateValues() {
            for (int val : values) {
                ((DistributionSummary) meter).record(val);
                count++;
                sum += val;
                if (val < 5) {
                    under5Count++;
                } else if (val < 50) {
                    from5To50Count++;
                } else if (val < 95) {
                    from50to95Count++;
                }
            }
        }

        @Override
        public void checkSerialization(JsonNode jsonNode) {
            checkPathHasValue(jsonNode, path(), count);
            checkPathHasValue(jsonNode, path(".sum"), sum);
            String[] temppath = path(".histogram");
            String[] path;
            if (!setSLOs) {
                path = new String[temppath.length-1];
                System.arraycopy(temppath, 0, path, 0, path.length);
            } else {
                path = temppath;
                path[3] = "values";
            }
            JsonNode histoNode1 = getPathNode(jsonNode, path, !setSLOs);
            if(!setSLOs) {
                assertThat(histoNode1).isNull();
                return;
            }
            assertThat(histoNode1.isArray()).isTrue();
            assertThat(histoNode1.size()).isEqualTo(3); //the 3 bucket boundaries of the SLOs 5,50,95
            assertThat(histoNode1.get(0).asDouble()).isEqualTo(5.0);
            assertThat(histoNode1.get(1).asDouble()).isEqualTo(50.0);
            assertThat(histoNode1.get(2).asDouble()).isEqualTo(95.0);
            path[3] = "counts";
            JsonNode histoNode2 = getPathNode(jsonNode, path);
            assertThat(histoNode2.isArray()).isTrue();
            assertThat(histoNode2.size()).isEqualTo(3); //the 3 counts of samples under boundaries of the SLOs 5,50,95
            assertThat(histoNode2.get(0).asInt()).isEqualTo(under5Count);
            assertThat(histoNode2.get(1).asInt()).isEqualTo(from5To50Count);
            assertThat(histoNode2.get(2).asInt()).isEqualTo(from50to95Count);
            path[3] = "type";
            JsonNode histoType = getPathNode(jsonNode, path);
            assertThat(histoType.isTextual()).isTrue();
            assertThat(histoType.asText()).isEqualTo("histogram");
        }
    }

    static class TestGauge extends TestMeter {
        List<String> list = new ArrayList<>(4);

        @Override
        String meternameExtension() {
            return "";
        }

        @Override
        public void addToMeterRegistry(MeterRegistry registry) {
            meter = Gauge.builder(metername(), list, List::size).register(registry);
        }

        @Override
        public void populateValues() {
            list.add("bananas");
            list.add("apples");
        }

        @Override
        public void checkSerialization(JsonNode jsonNode) {
            checkPathHasValue(jsonNode, path(), 2);
        }

    }

    static class TestTimer extends TestMeter {

        @Override
        String meternameExtension() {
            return ".sum.us";
        }

        @Override
        public void addToMeterRegistry(MeterRegistry registry) {
            meter = Timer.builder(metername()).register(registry);
        }

        @Override
        public void populateValues() {
            ((Timer) meter).record(430, TimeUnit.MILLISECONDS);
        }

        @Override
        public void checkSerialization(JsonNode jsonNode) {
            checkPathHasValue(jsonNode, path(), 430000);
        }

    }

    static class TestFunctionTimer extends TestMeter {
        double functionDoubleValue;
        long functionLongValue;

        @Override
        String meternameExtension() {
            return ".sum.us";
        }

        @Override
        public void addToMeterRegistry(MeterRegistry registry) {
            meter = FunctionTimer.builder(metername(), this, (o)->functionLongValue, (o)->functionDoubleValue, TimeUnit.MILLISECONDS).register(registry);
        }

        @Override
        public void populateValues() {
            functionLongValue = 2;
            functionDoubleValue = 440;
        }

        @Override
        public void checkSerialization(JsonNode jsonNode) {
            checkPathHasValue(jsonNode, path(), 440000);
            checkPathHasValue(jsonNode, path(".count"), 2);
        }

    }

    static class TestLongTaskTimer extends TestMeter {
        @Override
        String meternameExtension() {
            return ".count";
        }

        @Override
        public void addToMeterRegistry(MeterRegistry registry) {
            meter = LongTaskTimer.builder(metername()).register(registry);
        }

        @Override
        public void populateValues() {
            ((LongTaskTimer) meter).start();
            ((LongTaskTimer) meter).start();
        }

        @Override
        public void checkSerialization(JsonNode jsonNode) {
            checkPathHasValue(jsonNode, path(), 2);
        }

    }

}
