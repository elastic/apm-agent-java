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
package co.elastic.apm.agent.mongodb.configuration;

import co.elastic.apm.plugin.spi.WildcardMatcher;
import co.elastic.apm.plugin.spi.WildcardMatcherUtil;
import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationOptionProvider;
import org.stagemonitor.configuration.converter.AbstractValueConverter;
import org.stagemonitor.configuration.converter.ListValueConverter;

import java.util.Arrays;
import java.util.List;

public class MongoConfiguration extends ConfigurationOptionProvider implements co.elastic.apm.agent.mongodb.MongoConfiguration {

    private final ConfigurationOption<List<WildcardMatcher>> captureStatementCommands = ConfigurationOption
        .builder(new ListValueConverter<>(new WildcardMatcherValueConverter()), List.class)
        .key("mongodb_capture_statement_commands")
        .configurationCategory("MongoDB")
        .description("MongoDB command names for which the command document will be captured, limited to common read-only operations by default.\n" +
            "Set to ` \"\"` (empty) to disable capture, and `\"*\"` to capture all (which is discouraged as it may lead to sensitive information capture).\n" +
            "\n" +
            WildcardMatcherUtil.DOCUMENTATION
        )
        .dynamic(true)
        .buildWithDefault(Arrays.asList(
            WildcardMatcherUtil.valueOf("find"),
            WildcardMatcherUtil.valueOf("aggregate"),
            WildcardMatcherUtil.valueOf("count"),
            WildcardMatcherUtil.valueOf("distinct"),
            WildcardMatcherUtil.valueOf("mapReduce")
        ));

    public List<WildcardMatcher> getCaptureStatementCommands() {
        return captureStatementCommands.get();
    }

    private static class WildcardMatcherValueConverter extends AbstractValueConverter<WildcardMatcher> {

        @Override
        public WildcardMatcher convert(String wildcardString) throws IllegalArgumentException {
            return WildcardMatcherUtil.valueOf(wildcardString);
        }

        @Override
        public String toString(WildcardMatcher wildcardMatcher) {
            return wildcardMatcher.toString();
        }
    }
}
