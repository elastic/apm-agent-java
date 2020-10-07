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
package co.elastic.apm.agent.mongoclient;

import co.elastic.apm.agent.TestClassWithDependencyRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MongoClientSyncVersionIT {

    private TestClassWithDependencyRunner runner;

    private void init(String version) throws Exception {

        runner = new TestClassWithDependencyRunner("org.mongodb", "mongo-java-driver", version,
                MongoClientSyncInstrumentationIT.class, AbstractMongoClientInstrumentationTest.class);
    }

    public static Stream<Arguments> data() {
        return Arrays.asList(
                "3.11.1",
                "3.10.2",
                "3.9.0",
                "3.9.0",
                "3.8.2",
                "3.7.1",
                "3.6.4",
                "3.5.0",
                "3.4.3",
                "3.3.0",
                "3.2.2",
                "3.1.1",
                "3.0.4"
        ).stream().map(k -> Arguments.of(k)).collect(Collectors.toList()).stream();
    }

    @ParameterizedTest
    @MethodSource("data")
    public void testVersions(String version) throws Exception {
        init(version);

        runner.run();
    }

    @AfterEach
    public void tearDown() throws Exception {
        // judging from heap dumps, DefaultServerConnection seems to keep the class loader alive
        //runner.assertClassLoaderIsGCed();
    }
}
