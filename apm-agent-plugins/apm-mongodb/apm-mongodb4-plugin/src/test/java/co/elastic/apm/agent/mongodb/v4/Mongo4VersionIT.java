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

import co.elastic.apm.agent.testutils.JUnit4TestClassWithDependencyRunner;
import co.elastic.apm.agent.mongodb.AbstractMongoClientInstrumentationIT;
import co.elastic.apm.agent.common.util.Version;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(Parameterized.class)
public class Mongo4VersionIT {

    private final JUnit4TestClassWithDependencyRunner runner;

    public Mongo4VersionIT(String version, boolean legacyDriver) throws Exception {

        Version v = Version.of(version);

        List<String> dependencies = new ArrayList<>(Arrays.asList(
            "org.mongodb:mongodb-driver-sync:" + version,
            "org.mongodb:mongodb-driver-core:" + version,
            "org.mongodb:bson:" + version));

        if (legacyDriver) {
            dependencies.add("org.mongodb:mongodb-driver-legacy:" + version);
        }

        if (v.compareTo(Version.of("4.6.0")) >= 0) {
            dependencies.add("org.mongodb:bson-record-codec:" + version);
        }

        runner = new JUnit4TestClassWithDependencyRunner(dependencies,
            legacyDriver ? Mongo4LegacyIT.class : Mongo4SyncIT.class,
            AbstractMongoClientInstrumentationIT.class);
    }

    @Parameterized.Parameters(name = "{0} legacy-driver = {1}")
    public static Iterable<Object[]> data() {
        // whenever adding new versions to this list, you have to make sure that all transitive dependencies of the
        // driver are also explicitly included, over time the driver has moved to single monolithic jar to having more
        // and more dependencies instead of embedding them.
        List<String> versions = Arrays.asList(
            "5.5.0",
            "5.4.0",
            "5.3.0",
            "5.2.0",
            "5.1.0",
            "5.0.0",
            "4.11.0",
            "4.10.0",
            "4.9.0",
            "4.8.0",
            "4.7.0",
            "4.6.0",
            "4.5.0",
            "4.4.0",
            "4.3.0",
            "4.2.0",
            "4.1.0",
            "4.0.0"
        );
        List<Object[]> parameters = new ArrayList<>();
        for (int i = 0; i < versions.size(); i++) {
            String v = versions.get(i);
            parameters.add(new Object[]{v, false});
            if (i == 0) {
                // only test the legacy API once with the latest version to save some execution time
                parameters.add(new Object[]{v, true});
            }
        }
        return parameters;
    }

    @Test
    public void testVersions() throws Exception {
        runner.run();
    }

    @After
    public void tearDown() throws Exception {
    }
}
