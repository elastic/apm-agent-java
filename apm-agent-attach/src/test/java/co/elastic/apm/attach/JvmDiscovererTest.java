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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

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
        Properties sysProperties = new Properties();
        Path path = JvmDiscoverer.JpsFinder.getJpsPath(sysProperties);
        assertThat(path).asString()
            .describedAs("should use binary in path as fallback")
            .isEqualTo("jps");
    }

    @Test
    void getJpsPathWindows() {
        Properties sysProperties = new Properties();
        sysProperties.put("os.name", "Windows ME"); // the best one ever !
        Path path = JvmDiscoverer.JpsFinder.getJpsPath(sysProperties);
        assertThat(path).asString().isEqualTo("jps.exe");
        // note: we can't really test both windows+java.home set as it relies on absolute path resolution
    }

    @Test
    void getJpsPathCurrentJvm() {
        Path path = JvmDiscoverer.JpsFinder.getJpsPath(System.getProperties());
        assertThat(Files.isExecutable(path)).isTrue();
    }

}
