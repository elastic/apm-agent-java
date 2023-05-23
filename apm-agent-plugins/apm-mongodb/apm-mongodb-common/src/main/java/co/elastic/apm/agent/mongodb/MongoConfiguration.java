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
package co.elastic.apm.agent.mongodb;

import co.elastic.apm.agent.common.util.WildcardMatcher;
import co.elastic.apm.agent.matcher.WildcardMatcherValueConverter;
import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationOptionProvider;
import org.stagemonitor.configuration.converter.ListValueConverter;

import java.util.Arrays;
import java.util.List;

public class MongoConfiguration extends ConfigurationOptionProvider {

    private final ConfigurationOption<List<WildcardMatcher>> captureStatementCommands = ConfigurationOption
        .builder(new ListValueConverter<>(new WildcardMatcherValueConverter()), List.class)
        .key("mongodb_capture_statement_commands")
        .configurationCategory("Datastore")
        .description("MongoDB command names for which the command document will be captured, limited to common read-only operations by default.\n" +
            "Set to ` \"\"` (empty) to disable capture, and `\"*\"` to capture all (which is discouraged as it may lead to sensitive information capture).\n" +
            "\n" +
            WildcardMatcher.DOCUMENTATION
        )
        .dynamic(true)
        .buildWithDefault(Arrays.asList(
            WildcardMatcher.valueOf("find"),
            WildcardMatcher.valueOf("aggregate"),
            WildcardMatcher.valueOf("count"),
            WildcardMatcher.valueOf("distinct"),
            WildcardMatcher.valueOf("mapReduce")
        ));

    public List<WildcardMatcher> getCaptureStatementCommands() {
        return captureStatementCommands.get();
    }

}
