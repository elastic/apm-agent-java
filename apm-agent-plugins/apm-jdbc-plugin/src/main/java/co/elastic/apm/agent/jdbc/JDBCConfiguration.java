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

    private final ConfigurationOption<Boolean> use_instance_for_db_resource = ConfigurationOption.booleanOption()
        .key("use_instance_for_db_resource")
        .configurationCategory(JDBC_CATEGORY)
        .description("If set to `true`, the agent adds the db instance name to `destination.service.resource` of a JDBC span.")
        .dynamic(false)
        .tags("added[1.26.0]", "internal")
        .buildWithDefault(false);

    public boolean getUseInstanceForDbResource() {
        return use_instance_for_db_resource.get();
    }
}
