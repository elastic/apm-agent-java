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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import wiremock.org.apache.commons.codec.digest.DigestUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class ElasticApmAttacherTest {

    private List<File> toClean = new ArrayList<>();

    @AfterEach
    void cleanup() throws IOException {
        for (File file : toClean) {
            Files.delete(file.toPath());
        }
    }

    @Test
    void testHash() throws Exception {
        assertThat(ElasticApmAttacher.md5Hash(getClass().getResourceAsStream(ElasticApmAttacher.class.getSimpleName() + ".class")))
            .isEqualTo(DigestUtils.md5Hex(getClass().getResourceAsStream(ElasticApmAttacher.class.getSimpleName() + ".class")));
    }

    @Test
    void testCreateTempProperties() throws Exception {
        File tempProperties = ElasticApmAttacher.createTempProperties(Map.of("foo", "bär"));
        assertThat(tempProperties).isNotNull();

        toClean.add(tempProperties);

        Properties properties = readProperties(tempProperties);
        assertThat(properties.get("foo")).isEqualTo("bär");
    }

    @Test
    void testCreateTempPropertiesWithExternalConfig() throws IOException {
        Properties externalConfig = new Properties();
        externalConfig.putAll(Map.of("foo_ext", "bär_ext"));

        File externalConfigFile = File.createTempFile("external-config", ".tmp");
        toClean.add(externalConfigFile);
        externalConfig.store(new FileOutputStream(externalConfigFile), null);

        Map<String, String> config = Map.of(
            "foo", "bär",
            "config_file", externalConfigFile.getAbsolutePath());

        File tempProperties = ElasticApmAttacher.createTempProperties(config);
        toClean.add(tempProperties);

        Properties mergedProperties = readProperties(tempProperties);
        assertThat(mergedProperties)
            .containsEntry("foo", "bär")
            .containsEntry("foo_ext", "bär_ext");

    }

    private Properties readProperties(File propertyFile) throws IOException {
        Properties properties = new Properties();
        properties.load(new FileReader(propertyFile));
        return properties;
    }


}
