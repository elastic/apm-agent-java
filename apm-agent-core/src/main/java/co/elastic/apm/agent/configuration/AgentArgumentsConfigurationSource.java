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
package co.elastic.apm.agent.configuration;

import org.stagemonitor.configuration.source.AbstractConfigurationSource;
import org.stagemonitor.util.StringUtils;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

public class AgentArgumentsConfigurationSource extends AbstractConfigurationSource {

    private final Map<String, String> config;

    private AgentArgumentsConfigurationSource(Map<String, String> config) {
        this.config = config;
    }

    public static AgentArgumentsConfigurationSource parse(String agentAgruments) {
        final Map<String, String> configs = new HashMap<>();
        for (String config : StringUtils.split(agentAgruments, ';')) {
            final int indexOfEquals = config.indexOf('=');
            if (indexOfEquals < 1) {
                throw new IllegalArgumentException(String.format("%s is not a '=' separated key/value pair", config));
            }
            configs.put(config.substring(0, indexOfEquals).trim(), config.substring(indexOfEquals + 1).trim());
        }
        return new AgentArgumentsConfigurationSource(configs);
    }

    @Override
    @Nullable
    public String getValue(String key) {
        return config.get(key);
    }

    @Override
    public String getName() {
        return "-javaagent options";
    }

    Map<String, String> getConfig() {
        return config;
    }
}
