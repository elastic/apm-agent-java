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

import co.elastic.apm.agent.configuration.MetricsConfiguration;
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
        MetricsConfiguration config = mock(MetricsConfiguration.class);
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
        assertThat(serialized.size()).isEqualTo(2);
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

    static void checkPathHasValue(JsonNode jsonNode, String[] path, int value) {
        jsonNode = getPathNode(jsonNode, path);
        assertThat(jsonNode.asInt()).isEqualTo(value);
    }

    static JsonNode getPathNode(JsonNode jsonNode, String[] path) {
        assertThat(jsonNode).isNotNull();
        for (String element: path) {
            jsonNode = jsonNode.get(element);
            assertThat(jsonNode).isNotNull();
        }
        return jsonNode;
    }

    static boolean containsPathNode(JsonNode jsonNode, String[] path) {
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

    static JsonNode readJsonString(String jsonString) {
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

    interface TestMeter {
        void addToMeterRegistry(MeterRegistry registry);
        void populateValues();
        void checkSerialization(JsonNode jsonNode);
        Meter meter();
        boolean containsValues(JsonNode jsonNode);
    }

    static class TestCounter implements TestMeter {
        static String[] path = new String[]{"metricset", "samples", "counter", "value"};

        Counter counter;
        int value = 42;

        @Override
        public Meter meter() {
            return counter;
        }

        @Override
        public boolean containsValues(JsonNode jsonNode) {
            return containsPathNode(jsonNode, path);
        }

        @Override
        public void addToMeterRegistry(MeterRegistry registry) {
            counter = Counter.builder("counter").tags("tag", "value").register(registry);
        }

        @Override
        public void populateValues() {
            counter.increment(value);
        }

        @Override
        public void checkSerialization(JsonNode jsonNode) {
            checkPathHasValue(jsonNode, path, value);
        }

    }

    static class TestFunctionCounter implements TestMeter {
        static String[] path = new String[]{"metricset", "samples", "functioncounter", "value"};

        FunctionCounter functioncounter;
        long count;

        @Override
        public Meter meter() {
            return functioncounter;
        }

        @Override
        public boolean containsValues(JsonNode jsonNode) {
            return containsPathNode(jsonNode, path);
        }

        @Override
        public void addToMeterRegistry(MeterRegistry registry) {
            functioncounter = FunctionCounter.builder("functioncounter", this, (o)->count).register(registry);
        }

        @Override
        public void populateValues() {
            count = 48;
        }

        @Override
        public void checkSerialization(JsonNode jsonNode) {
            checkPathHasValue(jsonNode, path, (int) count);
        }

    }

    static class TestSummary implements TestMeter {
        static String[] path1 = new String[]{"metricset", "samples", "summary_example.count", "value"};

        DistributionSummary summary;
        int sum = 0;
        int count = 0;
        int under5Count = 0;
        int under50Count = 0;
        int under95Count = 0;
        int[] values = new int[]{22, 55, 66, 98};

        @Override
        public Meter meter() {
            return summary;
        }

        @Override
        public boolean containsValues(JsonNode jsonNode) {
            return containsPathNode(jsonNode, path1);
        }

        @Override
        public void addToMeterRegistry(MeterRegistry registry) {
            summary = DistributionSummary
                .builder("summary_example")
                .distributionStatisticBufferLength(20)
                .serviceLevelObjectives(5,50,95)
                .publishPercentileHistogram()
                .register(registry);
        }

        @Override
        public void populateValues() {
            for (int val : values) {
                summary.record(val);
                count++;
                sum += val;
                if (val < 5) {
                    under5Count++;
                }
                if (val < 50) {
                    under50Count++;
                }
                if (val < 95) {
                    under95Count++;
                }
            }
        }

        @Override
        public void checkSerialization(JsonNode jsonNode) {
            checkPathHasValue(jsonNode, path1, count);
            checkPathHasValue(jsonNode, new String[]{"metricset", "samples", "summary_example.sum", "value"}, sum);
            JsonNode histoNode1 = getPathNode(jsonNode, new String[]{"metricset", "samples", "summary_example.histogram", "values"});
            assertThat(histoNode1.isArray()).isTrue();
            assertThat(histoNode1.size()).isEqualTo(3); //the 3 bucket boundaries of the SLOs 5,50,95
            assertThat(histoNode1.get(0).asDouble()).isEqualTo(5.0);
            assertThat(histoNode1.get(1).asDouble()).isEqualTo(50.0);
            assertThat(histoNode1.get(2).asDouble()).isEqualTo(95.0);
            JsonNode histoNode2 = getPathNode(jsonNode, new String[]{"metricset", "samples", "summary_example.histogram", "counts"});
            assertThat(histoNode2.isArray()).isTrue();
            assertThat(histoNode2.size()).isEqualTo(3); //the 3 counts of samples under boundaries of the SLOs 5,50,95
            assertThat(histoNode2.get(0).asInt()).isEqualTo(under5Count);
            assertThat(histoNode2.get(1).asInt()).isEqualTo(under50Count);
            assertThat(histoNode2.get(2).asInt()).isEqualTo(under95Count);
        }
    }

    static class TestGauge implements TestMeter {
        static String[] path = new String[]{"metricset", "samples", "gaugetest", "value"};

        Gauge gauge;
        List<String> list = new ArrayList<>(4);

        @Override
        public Meter meter() {
            return gauge;
        }

        @Override
        public boolean containsValues(JsonNode jsonNode) {
            return containsPathNode(jsonNode, path);
        }

        @Override
        public void addToMeterRegistry(MeterRegistry registry) {
            gauge = Gauge.builder(path[2], list, List::size).register(registry);
        }

        @Override
        public void populateValues() {
            list.add("bananas");
            list.add("apples");
        }

        @Override
        public void checkSerialization(JsonNode jsonNode) {
            checkPathHasValue(jsonNode, path, 2);
        }

    }

    static class TestTimer implements TestMeter {
        static String[] path = new String[]{"metricset", "samples", "timertest.sum.us", "value"};

        Timer timer;

        @Override
        public Meter meter() {
            return timer;
        }

        @Override
        public boolean containsValues(JsonNode jsonNode) {
            return containsPathNode(jsonNode, path);
        }

        @Override
        public void addToMeterRegistry(MeterRegistry registry) {
            timer = Timer.builder("timertest").register(registry);
        }

        @Override
        public void populateValues() {
            timer.record(430, TimeUnit.MILLISECONDS);
        }

        @Override
        public void checkSerialization(JsonNode jsonNode) {
            checkPathHasValue(jsonNode, path, 430000);
        }

    }

    static class TestFunctionTimer implements TestMeter {
        static String[] path1 = new String[]{"metricset", "samples", "functiontimertest.sum.us", "value"};
        static String[] path2 = new String[]{"metricset", "samples", "functiontimertest.count", "value"};

        FunctionTimer functiontimer;
        double functionDoubleValue;
        long functionLongValue;

        @Override
        public Meter meter() {
            return functiontimer;
        }

        @Override
        public boolean containsValues(JsonNode jsonNode) {
            return containsPathNode(jsonNode, path1);
        }

        @Override
        public void addToMeterRegistry(MeterRegistry registry) {
            functiontimer = FunctionTimer.builder("functiontimertest", this, (o)->functionLongValue, (o)->functionDoubleValue, TimeUnit.MILLISECONDS).register(registry);
        }

        @Override
        public void populateValues() {
            functionLongValue = 2;
            functionDoubleValue = 440;
        }

        @Override
        public void checkSerialization(JsonNode jsonNode) {
            checkPathHasValue(jsonNode, path1, 440000);
            checkPathHasValue(jsonNode, path2, 2);
        }

    }

    static class TestLongTaskTimer implements TestMeter {
        static String[] path = new String[]{"metricset", "samples", "longtasktimertest.count", "value"};

        LongTaskTimer longtasktimer;

        @Override
        public Meter meter() {
            return longtasktimer;
        }

        @Override
        public boolean containsValues(JsonNode jsonNode) {
            return containsPathNode(jsonNode, path);
        }

        @Override
        public void addToMeterRegistry(MeterRegistry registry) {
            longtasktimer = LongTaskTimer.builder("longtasktimertest").register(registry);
        }

        @Override
        public void populateValues() {
            longtasktimer.start();
            longtasktimer.start();
        }

        @Override
        public void checkSerialization(JsonNode jsonNode) {
            checkPathHasValue(jsonNode, path, 2);
        }

    }

}
