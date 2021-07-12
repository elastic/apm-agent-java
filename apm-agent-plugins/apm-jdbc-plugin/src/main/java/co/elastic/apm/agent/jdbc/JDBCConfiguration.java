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
package co.elastic.apm.agent.jdbc;

import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationOptionProvider;

public class JDBCConfiguration extends ConfigurationOptionProvider {

    private static final String JDBC_CATEGORY = "JDBC";

    private final ConfigurationOption<Boolean> use_service_resource_auto_inference = ConfigurationOption.booleanOption()
        .key("use_jdbc_service_resource_auto_inference")
        .configurationCategory(JDBC_CATEGORY)
        .description("If set to `true`, the agent uses `type`, `subtype` and `db.instance` of a JDBC span to infer its `destination.service.resource`" +
            " instead of relying on the JDBC instrumentation to set it.\n" +
            "See for https://github.com/elastic/apm/blob/master/specs/agents/tracing-spans-destination.md#contextdestinationserviceresource for the inference algorithm.")
        .dynamic(false)
        .tags("added[1.25.0]")
        .buildWithDefault(false);

    public boolean getUseJDBCServiceResourceAutoInference() {
        return use_service_resource_auto_inference.get();
    }
}
