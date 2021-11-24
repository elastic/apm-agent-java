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
package co.elastic.apm.agent.configuration.source;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.stagemonitor.configuration.source.ConfigurationSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigSourcesTest {

    @Test
    void loadFromClasspath() throws IOException {
        ConfigurationSource source = ConfigSources.fromClasspath("test.elasticapm.properties", ClassLoader.getSystemClassLoader());
        assertThat(source).isNotNull();

        assertThat(source.getName()).isEqualTo("classpath:test.elasticapm.properties");
        assertThat(source.getValue("application_packages")).isEqualTo("co.elastic.apm");
        assertThat(source.getValue("log_level")).isEqualTo("DEBUG");

        // should be no-op
        source.reload();
    }

    @Test
    void loadFromFileSystem(@TempDir File tmp) throws IOException {

        Path config = Files.write(tmp.toPath().resolve("my-config.properties"), Arrays.asList(
            "item1=hello",
            "item2=world"
        )).toAbsolutePath();

        ConfigurationSource source = ConfigSources.fromFileSystem(config.toString());
        assertThat(source).isNotNull();
        assertThat(source.getName()).isEqualTo(config.toString());
        assertThat(source.getValue("item1")).isEqualTo("hello");
        assertThat(source.getValue("item2")).isEqualTo("world");


        // should support modification and reloading
        Files.write(config, Arrays.asList(
            "item1=byebye",
            "item2=world"
        ));
        source.reload();

        assertThat(source.getValue("item1")).isEqualTo("byebye");
        assertThat(source.getValue("item2")).isEqualTo("world");
    }

    @Test
    void loadFromAttachmentConfig(@TempDir File tmp) throws IOException {
        Path config = Files.write(tmp.toPath().resolve("my-config.properties"), Arrays.asList(
            "#source:config source description", // the configuration location hint is provided by 1st line as comment
            "item1=hello",
            "item2=world"
        )).toAbsolutePath();

        ConfigurationSource source = ConfigSources.fromRuntimeAttachParameters(config.toString());
        assertThat(source).isNotNull();

        assertThat(source.getName()).isEqualTo("Attachment configuration");
        assertThat(source.getValue("item1")).isEqualTo("hello");
        assertThat(source.getValue("item2")).isEqualTo("world");
    }

    @Test
    void tryToReloadDeletedConfig(@TempDir File tmp) throws IOException {
        // when a temporary file is used to provide configuration
        // it might be deleted and thus can't be reloaded afterwards
        Path config = Files.write(tmp.toPath().resolve("my-config.properties"), Arrays.asList(
            "msg=hello world"
        )).toAbsolutePath();

        ConfigurationSource source = ConfigSources.fromFileSystem(config.toString());
        assertThat(source).isNotNull();
        assertThat(source.getName()).isEqualTo(config.toString());

        Files.delete(config);
        assertThat(config).doesNotExist();

        source.reload();
    }

}
