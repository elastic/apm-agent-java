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
package co.elastic.apm.agent.configuration;

import org.stagemonitor.configuration.source.AbstractConfigurationSource;
import org.stagemonitor.util.StringUtils;

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
            final String[] split = StringUtils.split(config, '=');
            if (split.length != 2) {
                throw new IllegalArgumentException(String.format("%s is not a '=' separated key/value pair", config));
            }
            configs.put(split[0].trim(), split[1].trim());
        }
        return new AgentArgumentsConfigurationSource(configs);
    }

    @Override
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
