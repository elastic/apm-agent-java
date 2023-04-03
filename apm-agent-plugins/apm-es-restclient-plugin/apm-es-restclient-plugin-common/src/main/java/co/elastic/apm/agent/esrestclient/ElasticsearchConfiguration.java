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
package co.elastic.apm.agent.esrestclient;

import co.elastic.apm.agent.common.util.WildcardMatcher;
import co.elastic.apm.agent.matcher.WildcardMatcherValueConverter;
import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationOptionProvider;
import org.stagemonitor.configuration.converter.ListValueConverter;

import java.util.Arrays;
import java.util.List;

public class ElasticsearchConfiguration extends ConfigurationOptionProvider {
    private final ConfigurationOption<List<WildcardMatcher>> captureBodyUrls = ConfigurationOption
        .builder(new ListValueConverter<>(new WildcardMatcherValueConverter()), List.class)
        .key("elasticsearch_capture_body_urls")
        .configurationCategory("Datastore")
        .description("The URL path patterns for which the APM agent will capture the request body of outgoing requests to Elasticsearch made with the `elasticsearch-restclient` instrumentation. The default setting captures the body for Elasticsearch REST APIs searches and counts.\n" +
            "\n" +
            "The captured request body (if any) is stored on the `span.db.statement` field. Captured request bodies are truncated to a maximum length defined by <<config-long-field-max-length>>." +
            "\n" +
            WildcardMatcher.DOCUMENTATION
        )
        .tags("added[1.37.0]")
        .dynamic(true)
        .buildWithDefault(Arrays.asList(
            WildcardMatcher.valueOf("*_search"),
            WildcardMatcher.valueOf("*_msearch"),
            WildcardMatcher.valueOf("*_msearch/template"),
            WildcardMatcher.valueOf("*_search/template"),
            WildcardMatcher.valueOf("*_count"),
            WildcardMatcher.valueOf("*_sql"),
            WildcardMatcher.valueOf("*_eql/search"),
            WildcardMatcher.valueOf("*_async_search")
        ));

    public List<WildcardMatcher> getCaptureBodyUrls() {
        return captureBodyUrls.get();
    }
}
