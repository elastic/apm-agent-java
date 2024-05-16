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
package co.elastic.apm.agent.jdbc.helper;

import co.elastic.apm.agent.common.util.WildcardMatcher;
import co.elastic.apm.agent.tracer.configuration.WildcardMatcherValueConverter;
import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationOptionProvider;
import org.stagemonitor.configuration.converter.DoubleValueConverter;
import org.stagemonitor.configuration.converter.ListValueConverter;
import org.stagemonitor.configuration.converter.StringValueConverter;

import java.util.Arrays;
import java.util.List;

public class JdbcConfiguration extends ConfigurationOptionProvider {

    private final ConfigurationOption<List<String>> databaseMetaDataExclusionList = ConfigurationOption
        .builder(new ListValueConverter<String>(StringValueConverter.INSTANCE), List.class)
        .key("exclude_from_getting_username")
        .configurationCategory("Datastore")
        .description("If any of these strings match part of the package or class name of the DatabaseMetaData instance, getUserName() won't be called" +
            "\n" +
            WildcardMatcher.DOCUMENTATION
        )
        .tags("internal","added[1.49.0]")
        .dynamic(true)
        .buildWithDefault(Arrays.asList(
            "hikari",
            "c3p0"
        ));

    public List<String> getDatabaseMetaDataExclusionList() {
        return databaseMetaDataExclusionList.get();
    }

}
