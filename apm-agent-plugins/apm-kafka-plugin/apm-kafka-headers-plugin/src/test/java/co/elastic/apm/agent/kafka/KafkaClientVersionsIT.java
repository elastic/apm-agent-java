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
package co.elastic.apm.agent.kafka;

import co.elastic.apm.agent.TestClassWithDependencyRunner;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;

@RunWith(Parameterized.class)
public class KafkaClientVersionsIT {
    private final TestClassWithDependencyRunner runner;

    public KafkaClientVersionsIT(String version) throws Exception {
        runner = new TestClassWithDependencyRunner("org.apache.kafka", "kafka-clients", version,
            KafkaIT.class, KafkaIT.Consumer.class, KafkaIT.RecordIterationMode.class, KafkaIT.TestScenario.class,
            KafkaIT.ConsumerRecordConsumer.class);
    }

    @Parameterized.Parameters(name = "{0}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][]{
            // No real need to run both versions, the APIs are the same. Running both requires a separate module
            // because within a module that declares the dependency, the Kafka client is loaded by the app class loader
            // IN ADDITION to the test class loader. This works fine only when the tested version is the same as the
            // dependency version, probably due to some static initializations done by the first one loaded.
            // We use this test runner framework only so it runs from a class loader that is not the app class loader,
            // in order to verify that we don't have class visibility problems.
            {"2.4.0"}
        });
    }

    @Test
    public void testVersions() {
        runner.run();
    }
}
