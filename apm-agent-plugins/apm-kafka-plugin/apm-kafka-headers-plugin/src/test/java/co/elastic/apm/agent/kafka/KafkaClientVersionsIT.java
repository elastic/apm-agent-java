/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
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
package co.elastic.apm.agent.kafka;

import co.elastic.apm.agent.TestClassWithDependencyRunner;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

class KafkaClientVersionsIT {
    private TestClassWithDependencyRunner runner;

    private void setUp(String version) throws Exception {
        runner = new TestClassWithDependencyRunner("org.apache.kafka", "kafka-clients", version,
            KafkaIT.class, KafkaIT.Consumer.class, KafkaIT.RecordIterationMode.class, KafkaIT.TestScenario.class,
            KafkaIT.ConsumerRecordConsumer.class);
    }

    public static Stream<Arguments> data() {
        final List<Arguments> configurations = new ArrayList<>();
        configurations.add(Arguments.arguments("2.4.0"));
        return configurations.stream();
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testVersions(String version) throws Exception {
        setUp(version);
        runner.run();
    }
}
