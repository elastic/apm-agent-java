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
package co.elastic.apm.agent.profiler.asyncprofiler;

import co.elastic.apm.agent.configuration.SpyConfiguration;
import co.elastic.apm.agent.profiler.ProfilingConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.stagemonitor.configuration.ConfigurationRegistry;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public class AsyncProfilerTest {

    private ProfilingConfiguration profilerConfig;

    @BeforeEach
    void setUp() throws ReflectiveOperationException {
        ConfigurationRegistry config = SpyConfiguration.createSpyConfig();
        profilerConfig = config.getConfig(ProfilingConfiguration.class);

        // Ensure that the singleton AsyncProfiler is reset so a new instance is created for each test
        Field instance = AsyncProfiler.class.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null, null);
    }

    @Test
    void testShouldCopyLibToTempDirectory() {
        AsyncProfiler.getInstance(profilerConfig);

        File libDirectory = new File(profilerConfig.getProfilerLibDirectory());
        File[] libasyncProfilers = libDirectory.listFiles(getLibasyncProfilerFilenameFilter());
        assertThat(libasyncProfilers).hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    void testShouldCopyLibToSpecifiedDirectory() throws IOException {
        File parentDirectory = Files.createTempDirectory(null).toFile();
        parentDirectory.deleteOnExit();
        when(profilerConfig.getProfilerLibDirectory()).thenReturn(parentDirectory.getAbsolutePath());
        System.out.println(parentDirectory.getAbsolutePath());

        AsyncProfiler.getInstance(profilerConfig);

        File[] libasyncProfilers = parentDirectory.listFiles(getLibasyncProfilerFilenameFilter());
        assertThat(libasyncProfilers).hasSizeGreaterThanOrEqualTo(1);
    }

    private FilenameFilter getLibasyncProfilerFilenameFilter() {
        return (dir, name) -> name.startsWith("libasyncProfiler") && name.endsWith(".so");
    }
}
