/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 the original author or authors
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
package co.elastic.apm.sanitize;

import co.elastic.apm.configuration.CoreConfiguration;
import co.elastic.apm.matcher.WildcardMatcher;
import co.elastic.apm.report.processor.Processor;
import org.stagemonitor.configuration.ConfigurationRegistry;

import java.util.Map;

public abstract class AbstractSanitizingProcessor implements Processor {
    public static final String REDACTED = "[REDACTED]";
    private CoreConfiguration config;

    @Override
    public void init(ConfigurationRegistry configurationRegistry) {
        config = configurationRegistry.getConfig(CoreConfiguration.class);
    }

    protected void sanitizeMap(Map<String, ? super String> map) {
        for (Map.Entry<String, ? super String> entry : map.entrySet()) {
            if (isSensitive(entry.getKey())) {
                entry.setValue(REDACTED);
            }
        }
    }

    private boolean isSensitive(String key) {
        return WildcardMatcher.anyMatch(config.getSanitizeFieldNames(), key);
    }
}
