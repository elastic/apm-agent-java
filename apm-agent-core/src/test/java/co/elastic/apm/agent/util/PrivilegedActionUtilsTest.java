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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.AccessController;
import java.security.Permission;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static co.elastic.apm.agent.testutils.assertions.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledForJreRange(max = JRE.JAVA_17, disabledReason = "SecurityManager is not supported anymore")
class PrivilegedActionUtilsTest {

    private static final AtomicBoolean enabled = new AtomicBoolean(false);

    private static void enableSecurityManager() {
        enabled.set(false);
        System.setSecurityManager(new TestSecurityManager(enabled));
        enabled.set(true);
    }

    private static void disableSecurityManager() {
        enabled.set(false);
        System.setSecurityManager(null);
    }

    @Test
    void getEnv() {
        Map<String, String> envMap = System.getenv();
        String envKey = envMap.keySet().stream().findFirst().get();
        String envValue = envMap.get(envKey);

        testWithAndWithoutSecurityManager(() -> {
            assertThat(PrivilegedActionUtils.getEnv(envKey)).isEqualTo(envValue);
            assertThat(PrivilegedActionUtils.getEnv()).containsAllEntriesOf(envMap);
        });

    }

    @Test
    void getClassLoader() {
        ClassLoader cl = PrivilegedActionUtilsTest.class.getClassLoader();
        testWithAndWithoutSecurityManager(() -> assertThat(PrivilegedActionUtils.getClassLoader(PrivilegedActionUtilsTest.class)).isSameAs(cl));
    }

    @Test
    void getProtectionDomain() {
        ProtectionDomain pd = PrivilegedActionUtilsTest.class.getProtectionDomain();
        testWithAndWithoutSecurityManager(() -> assertThat(PrivilegedActionUtils.getProtectionDomain(PrivilegedActionUtilsTest.class)).isSameAs(pd));
    }

    @Test
    void getAndSetContextClassLoader() {
        ClassLoader originalCL = Thread.currentThread().getContextClassLoader();
        assertThat(originalCL).isNotNull();

        try {
            testWithAndWithoutSecurityManager(() -> {
                // when enabling the security manager, the current context CL might be overriden
                // thus we test our ability to change it by setting it to null
                PrivilegedActionUtils.setContextClassLoader(Thread.currentThread(), null);
                assertThat(PrivilegedActionUtils.getContextClassLoader(Thread.currentThread())).isNull();

                PrivilegedActionUtils.setContextClassLoader(Thread.currentThread(), originalCL);
                assertThat(PrivilegedActionUtils.getContextClassLoader(Thread.currentThread())).isSameAs(originalCL);
            });
        } finally {
            Thread.currentThread().setContextClassLoader(originalCL);
        }
    }

    @Test
    void newThread() {
        testWithAndWithoutSecurityManager(() -> PrivilegedActionUtils.newThread(() -> {
        }));
    }

    @Test
    void newFileInputStream(@TempDir Path tempDir) throws IOException {
        Path existingFile = tempDir.resolve("empty.test");
        Path missingFile = tempDir.resolve("missing.test");

        Files.createFile(existingFile);

        try {
            testWithAndWithoutSecurityManager(() -> {
                try (FileInputStream fis = PrivilegedActionUtils.newFileInputStream(existingFile.toFile())) {
                    assertThat(fis).isNotNull();

                    // file not found and other runtime errors should be perserved
                    assertThatThrownBy(() -> PrivilegedActionUtils.newFileInputStream(missingFile.toFile())).isInstanceOf(FileNotFoundException.class);
                    assertThatThrownBy(() -> PrivilegedActionUtils.newFileInputStream(null)).isInstanceOf(NullPointerException.class);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } finally {
            Files.deleteIfExists(existingFile);
        }
    }

    @Test
    void createDirectories(@TempDir Path tempDir) throws IOException {
        Path existingDir = tempDir.resolve("existing");
        Path toCreate = tempDir.resolve(Path.of("to-create", "child"));

        Files.createDirectories(existingDir);
        testWithAndWithoutSecurityManager(() -> {
            testPrivileged(() -> {
                assertThat(existingDir).isDirectory();
                assertThat(toCreate).doesNotExist();
            });

            try {
                PrivilegedActionUtils.createDirectories(existingDir);
                PrivilegedActionUtils.createDirectories(toCreate);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            testPrivileged(() -> {
                assertThat(existingDir).isDirectory();
                assertThat(toCreate).isDirectory();
                try {
                    Files.delete(toCreate);
                    Files.delete(toCreate.getParent());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        });
    }

    @Test
    void getProxySelector() {
        testWithAndWithoutSecurityManager(PrivilegedActionUtils::getDefaultProxySelector);
    }

    private static void testPrivileged(Runnable task) {
        AccessController.doPrivileged(new PrivilegedAction<Object>() {
            @Override
            public Object run() {
                task.run();
                return null;
            }
        });
    }

    void testWithAndWithoutSecurityManager(Runnable assertions) {
        assertions.run();
        try {
            enableSecurityManager();
            assertions.run();
        } finally {
            disableSecurityManager();
        }
        assertions.run();
    }

    private static class TestSecurityManager extends SecurityManager {

        private final AtomicBoolean enabled;

        public TestSecurityManager(AtomicBoolean enabled) {
            this.enabled = enabled;
        }

        @Override
        public void checkPermission(Permission perm) {
            if (!enabled.get()) {
                return;
            }
            checkPrivileged();
        }

        @Override
        public void checkPermission(Permission perm, Object context) {
            if (!enabled.get()) {
                return;
            }
            checkPrivileged();
        }

        private static void checkPrivileged() {
            StackTraceElement[] stackTrace = new RuntimeException().getStackTrace();
            for (StackTraceElement e : stackTrace) {
                if (e.getClassName().equals("java.security.AccessController") && e.getMethodName().equals("doPrivileged")) {
                    return;
                }
            }
            throw new SecurityException("missing privileged action");
        }
    }
}
