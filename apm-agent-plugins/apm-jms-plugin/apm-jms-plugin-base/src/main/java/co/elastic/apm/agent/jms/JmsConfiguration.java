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
package co.elastic.apm.agent.jms;

import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationOptionProvider;

import java.util.Collection;
import java.util.Collections;

public class JmsConfiguration extends ConfigurationOptionProvider {

    private final ConfigurationOption<Collection<String>> jmsListenerPackages = ConfigurationOption
        .stringsOption()
        .key("jms_listener_packages")
        .configurationCategory("JMS")
        .tags("internal")
        .description("Defines which packages contain JMS MessageListener implementations for instrumentation." +
            "\n" +
            "When set to a non-empty value, only the classes matching configuration will be instrumented.\n" +
            "This configuration option helps to make MessageListener type matching faster and improve application startup performance."
        )
        .dynamic(false)
        .buildWithDefault(Collections.<String>emptyList());

    public Collection<String> getJmsListenerPackages() {
        return jmsListenerPackages.get();
    }
}
