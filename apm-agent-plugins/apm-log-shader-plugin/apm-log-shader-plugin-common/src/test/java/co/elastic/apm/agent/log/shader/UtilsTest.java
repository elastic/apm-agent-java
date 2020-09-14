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
package co.elastic.apm.agent.log.shader;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.logging.LoggingConfiguration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public class UtilsTest extends AbstractInstrumentationTest {

    @Test
    void testShadePathComputation() {
        assertThat(Utils.computeShadeLogFilePath("/test/absolute/path/app.log")).isEqualTo("/test/absolute/path/app.ecs.json");
        assertThat(Utils.computeShadeLogFilePath("test/relative/path/app.log")).isEqualTo("test/relative/path/app.ecs.json");
        assertThat(Utils.computeShadeLogFilePath("/app.log")).isEqualTo("/app.ecs.json");
        assertThat(Utils.computeShadeLogFilePath("app.log")).isEqualTo("app.ecs.json");
    }

    @Test
    void testOverride() {
        when(config.getConfig(LoggingConfiguration.class).logShadingOverrideOriginalLogFiles()).thenReturn(true);
        assertThat(Utils.computeShadeLogFilePath("/test/absolute/path/app.log")).isEqualTo("/test/absolute/path/app.log");
        assertThat(Utils.computeShadeLogFilePath("/test/absolute/path/app")).isEqualTo("/test/absolute/path/app");
        assertThat(Utils.computeShadeLogFilePath("/test/absolute/path/app.log.1")).isEqualTo("/test/absolute/path/app.log.1");
    }

    @Test
    void testAlternativeShadeLogsDestination() {
        when(config.getConfig(LoggingConfiguration.class).getLogShadingDestinationDir()).thenReturn("/some/alt/location");
        assertThat(Utils.computeShadeLogFilePath("/test/absolute/path/app.log")).isEqualTo("/some/alt/location/app.ecs.json");
        assertThat(Utils.computeShadeLogFilePath("test/relative/path/app.log")).isEqualTo("/some/alt/location/app.ecs.json");
        assertThat(Utils.computeShadeLogFilePath("/app.log")).isEqualTo("/some/alt/location/app.ecs.json");
        assertThat(Utils.computeShadeLogFilePath("app.log")).isEqualTo("/some/alt/location/app.ecs.json");
    }

    @Test
    void testFileExtensionReplacement() {
        assertThat(Utils.replaceFileExtensionToEcsJson("app.log")).isEqualTo("app.ecs.json");
        assertThat(Utils.replaceFileExtensionToEcsJson("app")).isEqualTo("app.ecs.json");
        assertThat(Utils.replaceFileExtensionToEcsJson("app.some.log")).isEqualTo("app.some.ecs.json");
    }
}
