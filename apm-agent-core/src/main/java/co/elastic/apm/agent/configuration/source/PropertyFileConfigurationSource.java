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

    PropertyFileConfigurationSource(String location, Properties properties) {
        this.location = location;
        this.properties = properties;
    }

    @Override
    public void reload() {
        if (location == null) {
            return;
        }

        Properties newProperties = ConfigSources.getPropertiesFromFilesystem(location);
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

