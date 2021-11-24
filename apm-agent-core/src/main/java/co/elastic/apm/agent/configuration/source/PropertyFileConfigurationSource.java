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


import org.stagemonitor.configuration.source.AbstractConfigurationSource;
import org.stagemonitor.configuration.source.ConfigurationSource;
import org.stagemonitor.configuration.source.SimpleSource;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * A variation of {@link org.stagemonitor.configuration.source.PropertyFileConfigurationSource} (under Apache license 2.0)
 * which does not initialize a logger.
 * <p>
 * This is important when using this configuration source to configure the logger.
 * </p>
 */
public final class PropertyFileConfigurationSource extends AbstractConfigurationSource {

    /**
     * Path of the configuration location
     */
    private final String location;

    /**
     * Properties
     */
    private Properties properties;

    private PropertyFileConfigurationSource(String location, Properties properties) {
        this.location = location;
        this.properties = properties;
    }

    @Nullable
    public static ConfigurationSource fromRuntimeAttachParameters(String location) {
        return buildSimpleSource("Attachment configuration", getPropertiesFromFilesystem(location));
    }

    @Nullable
    public static ConfigurationSource fromClasspath(String location, ClassLoader classLoader) {
        return buildSimpleSource("classpath:" + location, getPropertiesFromClasspath(location, classLoader));
    }

    @Nullable
    public static ConfigurationSource fromFileSystem(@Nullable String location) {
        Properties properties = getPropertiesFromFilesystem(location);
        if (properties == null) return null;

        return new PropertyFileConfigurationSource(location, properties);
    }

    private static SimpleSource buildSimpleSource(String name, Properties properties) {
        if (properties == null) {
            return null;
        }
        SimpleSource source = new SimpleSource(name);
        for (String key : properties.stringPropertyNames()) {
            source.add(key, properties.getProperty(key));
        }
        return source;
    }

    private static Properties getPropertiesFromFilesystem(String location) {
        if (location == null) {
            return null;
        }

        Path file = Paths.get(location).toAbsolutePath();
        if (!Files.exists(file) || !Files.isReadable(file)) {
            return null;
        }

        return readProperties(file);
    }

    private static Properties getPropertiesFromClasspath(String classpathLocation, ClassLoader classLoader) {
        final Properties props = new Properties();
        try (InputStream resourceStream = classLoader.getResourceAsStream(classpathLocation)) {
            if (resourceStream != null) {
                props.load(resourceStream);
                return props;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static Properties readProperties(Path location) {
        Properties props = new Properties();
        try (Reader reader = Files.newBufferedReader(location)) {
            props.load(reader);
            return props;
        } catch (NoSuchFileException e) {
            // silently ignored as a transient configuration file might have been deleted
            return null;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void reload() {
        if (location == null) {
            return;
        }

        Properties newProperties = readProperties(new File(location).toPath());
        if (newProperties != null) {
            properties = newProperties;
        }
    }

    @Override
    public String getName() {
        return location;
    }

    @Override
    public String getValue(String key) {
        return properties.getProperty(key);
    }

}

