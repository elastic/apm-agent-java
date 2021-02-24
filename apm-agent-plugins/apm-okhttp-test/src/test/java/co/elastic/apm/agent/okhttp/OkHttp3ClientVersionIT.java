/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2021 Elastic and contributors
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
package co.elastic.apm.agent.okhttp;

import co.elastic.apm.agent.TestClassWithDependencyRunner;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.List;

@RunWith(Parameterized.class)
public class OkHttp3ClientVersionIT {
    private final TestClassWithDependencyRunner runner;
    private final TestClassWithDependencyRunner asyncTestRunner;

    public OkHttp3ClientVersionIT(List<String> dependencies) throws Exception {
        this.runner = new TestClassWithDependencyRunner(dependencies, OkHttp3ClientInstrumentationTest.class);
        this.asyncTestRunner = new TestClassWithDependencyRunner(dependencies, "co.elastic.apm.agent.okhttp.OkHttp3ClientAsyncInstrumentationTest", "co.elastic.apm.agent.okhttp.OkHttp3ClientAsyncInstrumentationTest$1");
    }

    @Parameterized.Parameters(name = "{0}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][]{
            // ivy can't automatically resolve the dependencies, so we'll have to manually type them in
            // check https://mvnrepository.com/artifact/com.squareup.okhttp3/okhttp for the respective versions of kotlin and okio
            {List.of("com.squareup.okhttp3:okhttp:3.0.1", "com.squareup.okio:okio:1.6.0")},
            {List.of("com.squareup.okhttp3:okhttp:3.3.1", "com.squareup.okio:okio:1.8.0")},
            {List.of("com.squareup.okhttp3:okhttp:3.6.0", "com.squareup.okio:okio:1.11.0")},
            {List.of("com.squareup.okhttp3:okhttp:3.9.0", "com.squareup.okio:okio:1.13.0")},
            {List.of("com.squareup.okhttp3:okhttp:3.12.13", "com.squareup.okio:okio:1.15.0")},
            {List.of("com.squareup.okhttp3:okhttp:3.14.9", "com.squareup.okio:okio:1.17.2")},
            {List.of("com.squareup.okhttp3:okhttp:4.3.0", "org.jetbrains.kotlin:kotlin-stdlib:1.3.61", "com.squareup.okio:okio:2.4.1")},
            {List.of("com.squareup.okhttp3:okhttp:4.4.1", "org.jetbrains.kotlin:kotlin-stdlib:1.3.61", "com.squareup.okio:okio:2.4.3")},
            {List.of("com.squareup.okhttp3:okhttp:4.5.0", "org.jetbrains.kotlin:kotlin-stdlib:1.3.70", "com.squareup.okio:okio:2.5.0")},
            {List.of("com.squareup.okhttp3:okhttp:4.6.0", "org.jetbrains.kotlin:kotlin-stdlib:1.3.71", "com.squareup.okio:okio:2.6.0")},
            {List.of("com.squareup.okhttp3:okhttp:4.7.1", "org.jetbrains.kotlin:kotlin-stdlib:1.3.71", "com.squareup.okio:okio:2.6.0")},
            {List.of("com.squareup.okhttp3:okhttp:4.8.1", "org.jetbrains.kotlin:kotlin-stdlib:1.3.72", "com.squareup.okio:okio:2.7.0")},
            {List.of("com.squareup.okhttp3:okhttp:4.9.1", "org.jetbrains.kotlin:kotlin-stdlib:1.4.10", "com.squareup.okio:okio:2.8.0")},
        });
    }

    @Test
    public void testSync() {
        runner.run();
    }

    @Test
    public void testAsync() {
        asyncTestRunner.run();
    }
}
