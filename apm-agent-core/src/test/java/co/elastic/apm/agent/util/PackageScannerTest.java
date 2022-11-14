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
package co.elastic.apm.agent.util;

import co.elastic.apm.agent.bci.ElasticApmAgent;
import net.bytebuddy.ByteBuddy;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class PackageScannerTest {

    @Test
    void getClassNames() throws Exception {
        assertThat(PackageScanner.getClassNames(getClass().getPackageName(), ElasticApmAgent.getAgentClassLoader()))
            .contains(PackageScanner.class.getName());
    }

    @Test
    void testScanJar() throws Exception {
        assertThat(PackageScanner.getClassNames(ByteBuddy.class.getPackageName(), ElasticApmAgent.getAgentClassLoader()))
            .contains(ByteBuddy.class.getName());
        // scan again to see verify there's no FileSystemAlreadyExistsException
        assertThat(PackageScanner.getClassNames(ByteBuddy.class.getPackageName(), ElasticApmAgent.getAgentClassLoader()))
            .contains(ByteBuddy.class.getName());
    }

    @Test
    void getClassNamesOfNonExistentPackage() throws Exception {
        assertThat(PackageScanner.getClassNames("foo.bar", ElasticApmAgent.getAgentClassLoader())).isEmpty();
    }

    @Test
    void threadInterruptSafe() throws InterruptedException, IOException {

        CountDownLatch end = new CountDownLatch(1);

        URL testJar = Test.class.getProtectionDomain().getCodeSource().getLocation();
        try (final URLClassLoader classLoader = new URLClassLoader(new URL[]{testJar})) {

            Thread t = new Thread() {
                @Override
                public void run() {

                    // intentionally interrupt the thread, which leaves the thread in "interrupted" state
                    interrupt();

                    try {
                        assertThat(PackageScanner.getClassNames(Test.class.getPackageName(), classLoader)).isNotEmpty();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }

                    assertThat(isInterrupted())
                        .describedAs("thread interrupted status should be preserved after getClassNames invocation")
                        .isTrue();

                    end.countDown();
                }
            };

            t.start();

            assertThat(end.await(1, TimeUnit.SECONDS))
                .describedAs("abnormal test task termination")
                .isTrue();
        }
    }



}
