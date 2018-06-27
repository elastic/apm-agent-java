/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
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
package co.elastic.apm.configuration.source;


import org.stagemonitor.configuration.source.AbstractConfigurationSource;
import org.stagemonitor.util.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * A variation of {@link org.stagemonitor.configuration.source.PropertyFileConfigurationSource} which does not initialize a logger.
 */
public final class PropertyFileConfigurationSource extends AbstractConfigurationSource {

    private final String location;
    private Properties properties;
    private File file;
    private boolean writeable;

    public PropertyFileConfigurationSource(String location) {
        this.location = location;
        try {
            this.file = IOUtils.getFile(location);
            this.writeable = file.canWrite();
        } catch (Exception e) {
            this.writeable = false;
        }
        reload();
    }

    public static boolean isPresent(String location) {
        return getProperties(location) != null;
    }

    private static Properties getProperties(String location) {
        if (location == null) {
            return null;
        }
        Properties props = getFromClasspath(location, org.stagemonitor.configuration.source.PropertyFileConfigurationSource.class.getClassLoader());
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
    public boolean isSavingPersistent() {
        return true;
    }

    @Override
    public String getName() {
        return location;
    }

    @Override
    public String getValue(String key) {
        return properties.getProperty(key);
    }

    @Override
    public boolean isSavingPossible() {
        return writeable;
    }

    @Override
    public void save(String key, String value) throws IOException {
        if (file != null) {
            synchronized (this) {
                properties.put(key, value);
                final FileOutputStream out = new FileOutputStream(file);
                properties.store(out, null);
                out.flush();
                out.close();
            }
        } else {
            throw new IOException(location + " is not writeable");
        }
    }

}

