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
package co.elastic.apm.agent.profiler.asyncprofiler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FilenameFilter;

import static co.elastic.apm.agent.profiler.asyncprofiler.AsyncProfiler.SAFEMODE_SYSTEM_PROPERTY_NAME;
import static org.assertj.core.api.Assertions.assertThat;

@DisabledOnOs(OS.WINDOWS)
public class AsyncProfilerTest {

    @BeforeEach
    void setUp() {
        AsyncProfiler.reset();
    }

    @Test
    void testShouldCopyLibToTempDirectory() {
        String defaultTempDirectory = System.getProperty("java.io.tmpdir");
        AsyncProfiler.getInstance(defaultTempDirectory, 0);
        assertThat(Integer.valueOf(System.getProperty(SAFEMODE_SYSTEM_PROPERTY_NAME))).isEqualTo(0);

        File libDirectory = new File(defaultTempDirectory);
        File[] libasyncProfilers = libDirectory.listFiles(getLibasyncProfilerFilenameFilter());
        assertThat(libasyncProfilers).hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    void testShouldCopyLibToSpecifiedDirectory(@TempDir File nonDefaultTempDirectory) {
        AsyncProfiler.getInstance(nonDefaultTempDirectory.getAbsolutePath(), 6);
        assertThat(Integer.valueOf(System.getProperty(SAFEMODE_SYSTEM_PROPERTY_NAME))).isEqualTo(6);

        File[] libasyncProfilers = nonDefaultTempDirectory.listFiles(getLibasyncProfilerFilenameFilter());
        assertThat(libasyncProfilers).hasSizeGreaterThanOrEqualTo(1);
    }

    private FilenameFilter getLibasyncProfilerFilenameFilter() {
        return (dir, name) -> name.startsWith("libasyncProfiler") && name.endsWith(".so");
    }
}
