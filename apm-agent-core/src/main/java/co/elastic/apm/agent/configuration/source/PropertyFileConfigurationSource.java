/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.agent.configuration.source;


import org.stagemonitor.configuration.source.AbstractConfigurationSource;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * A variation of {@link org.stagemonitor.configuration.source.PropertyFileConfigurationSource} (under Apache license 2.0)
 * which does not initialize a logger.
 * <p>
 * This is important when using this configuration source to configure the logger.
 * </p>
 */
public final class PropertyFileConfigurationSource extends AbstractConfigurationSource {

    private final String location;
    private Properties properties;

    public PropertyFileConfigurationSource(String location) {
        this.location = location;
        reload();
    }

    public static boolean isPresent(String location) {
        return getProperties(location) != null;
    }

    private static Properties getProperties(String location) {
        if (location == null) {
            return null;
        }
        Properties props = getFromClasspath(location, ClassLoader.getSystemClassLoader());
        if (props == null) {
            props = getFromFileSystem(location);
        }
        return props;
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

    private static Properties getFromFileSystem(String location) {
        Properties props = new Properties();
        try (InputStream input = new FileInputStream(location)) {
            props.load(input);
            return props;
        } catch (FileNotFoundException ex) {
            return null;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void reload() {
        properties = getProperties(location);
        if (properties == null) {
            properties = new Properties();
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

