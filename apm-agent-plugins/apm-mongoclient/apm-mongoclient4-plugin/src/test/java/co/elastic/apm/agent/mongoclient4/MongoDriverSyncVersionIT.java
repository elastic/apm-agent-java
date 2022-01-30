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
package co.elastic.apm.agent.mongoclient4;

import co.elastic.apm.agent.TestClassWithDependencyRunner;
import java.util.List;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;

@RunWith(Parameterized.class)
public class MongoDriverSyncVersionIT {

    private final TestClassWithDependencyRunner runner;

    public MongoDriverSyncVersionIT(String version) throws Exception {
        List<String> dependencies = Arrays.asList(
            "org.mongodb:mongodb-driver-sync:" + version,
            "org.mongodb:bson:" + version,
            "org.mongodb:mongodb-driver-core:" + version
        );
        runner = new TestClassWithDependencyRunner(dependencies, MongoDriverSyncInstrumentationIT.class,
            AbstractMongoDriverInstrumentationTest.class);
    }

    @Parameterized.Parameters(name= "{0}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][]{
            {"4.4.1"},
            {"4.3.4"},
            {"4.2.3"},
            {"4.1.2"},
            {"4.0.6"}
        });
    }

    @Test
    public void testVersions() throws Exception {
        runner.run();
    }

    @After
    public void tearDown() throws Exception {
        // judging from heap dumps, DefaultServerConnection seems to keep the class loader alive
        //runner.assertClassLoaderIsGCed();
    }
}
