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
package co.elastic.apm.agent.loginstr.reformatting;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.logging.LogEcsReformatting;
import co.elastic.apm.agent.logging.LoggingConfiguration;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public class UtilsTest extends AbstractInstrumentationTest {

    private static final String fileSeparator = System.getProperty("file.separator");

    @Nullable
    private final String logEcsFormattingDestinationDir = config.getConfig(LoggingConfiguration.class).getLogEcsFormattingDestinationDir();

    private String computeReformattedLogFilePathWithConfiguredDir(String logFilePath) {
        return Utils.computeLogReformattingFilePath(logFilePath, logEcsFormattingDestinationDir);
    }

    @Test
    void testReformattedPathComputation() {
        assertThat(computeReformattedLogFilePathWithConfiguredDir("/test/absolute/path/app.log")).isEqualTo(replaceFileSeparator("/test/absolute/path/app.ecs.json"));
        assertThat(computeReformattedLogFilePathWithConfiguredDir("test/relative/path/app.log")).isEqualTo(replaceFileSeparator("test/relative/path/app.ecs.json"));
        assertThat(computeReformattedLogFilePathWithConfiguredDir("/app.log")).isEqualTo(replaceFileSeparator("/app.ecs.json"));
        assertThat(computeReformattedLogFilePathWithConfiguredDir("app.log")).isEqualTo(replaceFileSeparator("app.ecs.json"));
    }

    @Test
    void testReplace() {
        when(config.getConfig(LoggingConfiguration.class).getLogEcsReformatting()).thenReturn(LogEcsReformatting.REPLACE);
        assertThat(computeReformattedLogFilePathWithConfiguredDir("/test/absolute/path/app.log")).isEqualTo(replaceFileSeparator("/test/absolute/path/app.ecs.json"));
        assertThat(computeReformattedLogFilePathWithConfiguredDir("/test/absolute/path/app")).isEqualTo(replaceFileSeparator("/test/absolute/path/app.ecs.json"));
        assertThat(computeReformattedLogFilePathWithConfiguredDir("/test/absolute/path/app.log.1")).isEqualTo(replaceFileSeparator("/test/absolute/path/app.log.ecs.json"));
    }

    @Test
    void testAlternativeLogReformattingDestination_AbsolutePath() {
        String reformattedDir = "/some/alt/location";
        assertThat(Utils.computeLogReformattingFilePath("/test/absolute/path/app.log", reformattedDir)).isEqualTo(replaceFileSeparator("/some/alt/location/app.ecs.json"));
        assertThat(Utils.computeLogReformattingFilePath("test/relative/path/app.log", reformattedDir)).isEqualTo(replaceFileSeparator("/some/alt/location/app.ecs.json"));
        assertThat(Utils.computeLogReformattingFilePath("/app.log", reformattedDir)).isEqualTo(replaceFileSeparator("/some/alt/location/app.ecs.json"));
        assertThat(Utils.computeLogReformattingFilePath("app.log", reformattedDir)).isEqualTo(replaceFileSeparator("/some/alt/location/app.ecs.json"));
    }

    @Test
    void testAlternativeLogsReformattingDestination_RelativePath() {
        String reformattedDir = "some/alt/location";
        assertThat(Utils.computeLogReformattingFilePath("/test/absolute/path/app.log", reformattedDir)).isEqualTo(replaceFileSeparator("/test/absolute/path/some/alt/location/app.ecs.json"));
        assertThat(Utils.computeLogReformattingFilePath("test/relative/path/app.log", reformattedDir)).isEqualTo(replaceFileSeparator("test/relative/path/some/alt/location/app.ecs.json"));
        assertThat(Utils.computeLogReformattingFilePath("/app.log", reformattedDir)).isEqualTo(replaceFileSeparator("/some/alt/location/app.ecs.json"));
        assertThat(Utils.computeLogReformattingFilePath("app.log", reformattedDir)).isEqualTo(replaceFileSeparator("some/alt/location/app.ecs.json"));
    }

    @Test
    void testFileExtensionReplacement() {
        assertThat(Utils.replaceFileExtensionToEcsJson("app.log")).isEqualTo("app.ecs.json");
        assertThat(Utils.replaceFileExtensionToEcsJson("app")).isEqualTo("app.ecs.json");
        assertThat(Utils.replaceFileExtensionToEcsJson("app.some.log")).isEqualTo("app.some.ecs.json");
    }

    private String replaceFileSeparator(String input) {
        return input.replace("/", fileSeparator);
    }

}
