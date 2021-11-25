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
package co.elastic.apm.attach;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class ElasticApmAttacherTest {

    @Test
    void testCreateTempProperties(@TempDir File tmp) throws Exception {
        File tempProperties = ElasticApmAttacher.createTempProperties(Map.of("foo", "bär"), tmp);
        assertThat(tempProperties).isNotNull();

        assertThat(tempProperties.getParentFile())
            .describedAs("property files should be created at root of tmp folder")
            .isEqualTo(tmp);

        Properties properties = readProperties(tempProperties);
        assertThat(properties)
            .hasSize(1)
            .containsEntry("foo", "bär");
    }

    @Test
    void testCreateEmptyConfigDoesNotCreateFile(@TempDir File tmp) {
        File tempProperties = ElasticApmAttacher.createTempProperties(Map.of(), tmp);
        assertThat(tempProperties).isNull();
        assertThat(tmp.listFiles())
            .describedAs("no file should be created in temp folder")
            .isEmpty();
    }

    private Properties readProperties(File propertyFile) throws IOException {
        Properties properties = new Properties();
        try (FileReader fileReader = new FileReader(propertyFile)) {
            properties.load(fileReader);
        }
        return properties;
    }


}
