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
package co.elastic.apm.attach;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;


class JvmDiscovererTest {

    @Test
    void discoverHotspotJvms() {
        JvmDiscoverer.ForHotSpotVm discoverer = JvmDiscoverer.ForHotSpotVm.withDefaultTempDir();
        assertThat(discoverer.isAvailable())
            .describedAs("HotSpot JVM discovery should be available")
            .isTrue();
        assertThat(discoverer.discoverJvms()).contains(new JvmInfo(String.valueOf(ProcessHandle.current().pid()), null));
    }

    @Test
    void jpsShouldIgnoreJps() throws Exception {

        JvmDiscoverer discoverer = JvmDiscoverer.Jps.INSTANCE;
        assertThat(discoverer.isAvailable())
            .describedAs("jps JVM discovery should be available")
            .isTrue();
        for (JvmInfo jvm : discoverer.discoverJvms()) {
            Optional.ofNullable(jvm.packageOrPathOrJvmProperties)
                .ifPresent((s)-> assertThat(s).doesNotContain("sun.tool.jps.Jps"));
        }
    }

    @Test
    void getJpsPathNoJavaHome() {
        checkExpectedJpsPaths(new Properties(), new HashMap<String, String>(), Paths.get("jps"));
    }

    @Test
    void getJpsPathJavaHomeProperties() {
        Properties sysProperties = new Properties();
        Map<String,String> env = new HashMap<String,String>();

        Path javaHomeProperties = Paths.get("java", "home");
        sysProperties.put("java.home", javaHomeProperties.toString());

        checkExpectedJpsPaths(sysProperties, env,
            javaHomeProperties.resolve("bin").resolve("jps"),
            javaHomeProperties.resolve("..").resolve("bin").resolve("jps"),
            Paths.get("jps"));
    }

    @Test
    void getJpsPathJavaHomeEnv() {
        Properties sysProperties = new Properties();
        Map<String,String> env = new HashMap<String,String>();

        Path javaHomeEnv = Paths.get("usr", "local", "java");
        env.put("JAVA_HOME", javaHomeEnv.toString());

        checkExpectedJpsPaths(sysProperties, env,
            javaHomeEnv.resolve("bin").resolve("jps"),
            javaHomeEnv.resolve("..").resolve("bin").resolve("jps"),
            Paths.get("jps"));
    }

    @Test
    void getJpsPathJavaHomeEnvAndProperties() {
        Properties sysProperties = new Properties();
        Map<String, String> env = new HashMap<String, String>();

        Path javaHomeEnv = Paths.get("usr", "local", "java");
        env.put("JAVA_HOME", javaHomeEnv.toString());

        Path javaHomeProperties = Paths.get("java", "home");
        sysProperties.put("java.home", javaHomeProperties.toString());

        checkExpectedJpsPaths(sysProperties, env,
            javaHomeEnv.resolve("bin").resolve("jps"),
            javaHomeEnv.resolve("..").resolve("bin").resolve("jps"),
            javaHomeProperties.resolve("bin").resolve("jps"),
            javaHomeProperties.resolve("..").resolve("bin").resolve("jps"),
            Paths.get("jps"));
    }


    @Test
    void getJpsPathWindows() {
        Properties sysProperties = new Properties();
        sysProperties.put("os.name", "Windows ME"); // the best one ever !

        checkExpectedJpsPaths(sysProperties, new HashMap<String,String>(), Paths.get("jps.exe"));
        // note: we can't really test both windows+java.home set as it relies on absolute path resolution
    }

    @Test
    void getJpsNotFound() {
        assertThrows(IllegalStateException.class, () -> JvmDiscoverer.JpsFinder.getJpsPath(new Properties(), new HashMap<String, String>()));
    }

    @Test
    void getJpsPathCurrentJvm() {
        Path path = JvmDiscoverer.JpsFinder.getJpsPath(System.getProperties(), System.getenv());
        assertThat(Files.isExecutable(path)).isTrue();
    }

    void checkExpectedJpsPaths(Properties sysProperties, Map<String, String> env, Path... expectedPaths) {
        List<Path> paths = JvmDiscoverer.JpsFinder.getJpsPaths(sysProperties, env);
        assertThat(paths)
            .containsExactly(expectedPaths);
    }

}
