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

import co.elastic.apm.agent.configuration.converter.ListValueConverter;
import co.elastic.apm.agent.matcher.WildcardMatcher;
import co.elastic.apm.agent.matcher.WildcardMatcherValueConverter;
import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationOptionProvider;

import java.util.Collections;
import java.util.List;

public class JDBCConfiguration extends ConfigurationOptionProvider {

    private static final String JDBC_CATEGORY = "JDBC";

    private final ConfigurationOption<List<WildcardMatcher>> sqls_excluded_from_instrumentation = ConfigurationOption
        .builder(new ListValueConverter<>(new WildcardMatcherValueConverter()), List.class)
        .key("sqls_excluded_from_jdbc_instrumentation")
        .configurationCategory(JDBC_CATEGORY)
        .description("A list of SQLs that should be ignored and not captured as spans.\n\n" + WildcardMatcher.DOCUMENTATION)
        .dynamic(false)
        .tags("added[1.30.0]")
        .buildWithDefault(Collections.<WildcardMatcher>emptyList());

    public List<WildcardMatcher> getSQLsExcludedFromInstrumentation() {
        return sqls_excluded_from_instrumentation.get();
    }
}
