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
package co.elastic.apm.agent.mongodb.v4;

import co.elastic.apm.agent.TestClassWithDependencyRunner;
import co.elastic.apm.agent.mongodb.AbstractMongoClientInstrumentationTest;
import co.elastic.apm.agent.util.Version;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(Parameterized.class)
public class Mongo4VersionIT {

    private final TestClassWithDependencyRunner runner;

    public Mongo4VersionIT(String version) throws Exception {

        Version v = Version.of(version);

        List<String> dependencies = new ArrayList<>(Arrays.asList(
            "org.mongodb:mongodb-driver-sync:" + version,
            "org.mongodb:mongodb-driver-legacy:" + version,
            "org.mongodb:mongodb-driver-core:" + version,
            "org.mongodb:bson:" + version));

        if (v.compareTo(Version.of("4.6.0")) >= 0) {
            dependencies.add("org.mongodb:bson-record-codec:" + version);
        }

        Class<?> testClass = Mongo4SyncTest.class;

        runner = new TestClassWithDependencyRunner(dependencies,
            testClass, AbstractMongoClientInstrumentationTest.class);
    }

    @Parameterized.Parameters(name= "{0}")
    public static Iterable<Object[]> data() {
        // whenever adding new versions to this list, you have to make sure that all transitive dependencies of the
        // driver are also explicitly included, over time the driver has moved to single monolithic jar to having more
        // and more dependencies instead of embedding them.
        return Arrays.asList(new Object[][]{
            {"4.6.0"},
            {"4.5.0"},
            {"4.4.0"},
            {"4.3.0"},
            {"4.2.0"},
            {"4.1.0"},
            {"4.0.0"},
        });
    }

    @Test
    public void testVersions() throws Exception {
        runner.run();
    }

    @After
    public void tearDown() throws Exception {
    }
}
