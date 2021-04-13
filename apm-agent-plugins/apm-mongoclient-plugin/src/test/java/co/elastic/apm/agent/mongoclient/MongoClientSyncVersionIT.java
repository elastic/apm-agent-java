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
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;

@RunWith(Parameterized.class)
public class MongoClientSyncVersionIT {

    private final TestClassWithDependencyRunner runner;

    public MongoClientSyncVersionIT(String version) throws Exception {
        runner = new TestClassWithDependencyRunner("org.mongodb", "mongo-java-driver", version,
            MongoClientSyncInstrumentationIT.class, AbstractMongoClientInstrumentationTest.class);
    }

    @Parameterized.Parameters(name= "{0}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][]{
            {"3.11.1"},
            {"3.10.2"},
            {"3.9.0"},
            {"3.9.0"},
            {"3.8.2"},
            {"3.7.1"},
            {"3.6.4"},
            {"3.5.0"},
            {"3.4.3"},
            {"3.3.0"},
            {"3.2.2"},
            {"3.1.1"},
            {"3.0.4"}
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
