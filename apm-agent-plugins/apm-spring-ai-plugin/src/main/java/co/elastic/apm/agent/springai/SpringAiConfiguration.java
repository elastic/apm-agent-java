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
package co.elastic.apm.agent.springai;

import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationOptionProvider;

public class SpringAiConfiguration extends ConfigurationOptionProvider {

    private final ConfigurationOption<Boolean> captureSpringAiContent = ConfigurationOption.booleanOption()
        .key("capture_spring_ai_content")
        .configurationCategory("AI")
        .tags("security", "performance", "experimental", "added[1.58.0]")
        .description("Enables capture of Spring AI prompt and response messages, embedding inputs, vector store documents and queries, and tool arguments and results.\n\n" +
            "Captured content can contain sensitive information and can significantly increase storage usage. Content capture is disabled by default. " +
            "Individual captured content values are truncated according to `long_field_max_length`.")
        .dynamic(true)
        .buildWithDefault(false);

    public boolean isCaptureSpringAiContent() {
        return captureSpringAiContent.get();
    }
}
