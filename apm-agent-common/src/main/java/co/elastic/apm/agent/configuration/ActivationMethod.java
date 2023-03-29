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
package co.elastic.apm.agent.configuration;

public enum ActivationMethod {
    // Set explicitly by the process starting the agent
    K8S_ATTACH, // https://github.com/elastic/apm-mutating-webhook
    AWS_LAMBDA_LAYER, // Only if installed by using layers (as other metadata already identifies lambda)
    FLEET, // Fleet using 'java -jar apm-agent-attach-cli.jar ...' to attach (directly or through webhook)
    APM_AGENT_ATTACH_CLI, // 'java -jar apm-agent-attach-cli.jar ...' used
    PROGRAMMATIC_SELF_ATTACH, // ElasticApmAttacher.attach();
    AZURE_FUNCTIONS, //?

    //Inferred
    JAVAAGENT_FLAG, // -javaagent:... command-line option used
    ENV_ATTACH, // JAVA_TOOL_OPTIONS env var used to specify -javaagent option

    UNKNOWN;

    public String toReferenceString() {
        return toString().replace('_', '-').toLowerCase();
    }
}
