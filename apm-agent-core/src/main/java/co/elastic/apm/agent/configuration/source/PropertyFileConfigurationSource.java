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

import javax.annotation.Nullable;
import java.io.BufferedReader;
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

    private static final String SOURCE_PREFIX = "#source:";

    /**
     * Path of the configuration location
     */
    private final String location;

    /**
     * User-friendly configuration source path, might be a path or a classpath path
     */
    private final String sourceName;

    /**
     * Properties
     */
    private Properties properties;

    private final boolean fromClasspath;

    private PropertyFileConfigurationSource(String location, boolean fromClasspath, String sourceName, Properties properties) {
        this.location = location;
        this.fromClasspath = fromClasspath;
        this.sourceName = sourceName;
        this.properties = properties;
    }

    public static Properties getFromClasspath(String classpathLocation, ClassLoader classLoader) {
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

    @Nullable
    public static PropertyFileConfigurationSource fromClasspath(String location) {
        Properties properties = getFromClasspath(location, ClassLoader.getSystemClassLoader());
        if (properties == null) {
            return null;
        }
        return new PropertyFileConfigurationSource(location, true, "classpath:" + location, properties);
    }

    @Nullable
    public static PropertyFileConfigurationSource fromFileSystem(@Nullable String location) {
        if (location == null) {
            return null;
        }

        Path file = Paths.get(location).toAbsolutePath();
        if (!Files.exists(file) || !Files.isReadable(file)) {
            return null;
        }

        Properties properties = readProperties(file);
        if (null == properties) {
            return null;
        }

        String sourceName = parseSource(file);
        if (sourceName == null) {
            sourceName = file.toString();
        }

        return new PropertyFileConfigurationSource(location, false, sourceName, properties);
    }

    private static String parseSource(Path file) {
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String firstLine = reader.readLine();
            if (firstLine != null && firstLine.startsWith(SOURCE_PREFIX)) {
                return firstLine.substring(SOURCE_PREFIX.length());
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
        if (location == null || fromClasspath) {
            return;
        }

        Properties newProperties = readProperties(new File(location).toPath());
        if (newProperties != null) {
            properties = newProperties;
        }
    }

    @Override
    public String getName() {
        return sourceName;
    }

    @Override
    public String getValue(String key) {
        return properties.getProperty(key);
    }

}

