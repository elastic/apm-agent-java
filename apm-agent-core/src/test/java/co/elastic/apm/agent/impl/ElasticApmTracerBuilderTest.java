/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.agent.impl;

import co.elastic.apm.agent.configuration.CoreConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.stagemonitor.configuration.ConfigurationRegistry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ElasticApmTracerBuilderTest {

    private static final String CONFIG_FILE_LOCATION_PROPERTY = "elastic.apm.config_file_location";

    @AfterEach
    void tearDown() {
        System.clearProperty(CONFIG_FILE_LOCATION_PROPERTY);
    }

    @Test
    void testConfigFileLocation(@TempDir Path tempDir) throws IOException {
        Path file = Files.createFile(tempDir.resolve("elastic-apm-test.properties"));
        Files.write(file, List.of("instrument=false"));
        System.setProperty(CONFIG_FILE_LOCATION_PROPERTY, file.toString());

        ConfigurationRegistry configurationRegistry = new ElasticApmTracerBuilder().build().getConfigurationRegistry();
        CoreConfiguration config = configurationRegistry.getConfig(CoreConfiguration.class);

        // tests that changing non-dynamic properties also works
        assertThat(config.isInstrument()).isFalse();
        configurationRegistry.getString("config_file_location");
        assertThat(configurationRegistry.getString("config_file_location")).isEqualTo(file.toString());
    }
}
