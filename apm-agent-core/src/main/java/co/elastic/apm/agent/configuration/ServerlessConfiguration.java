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

import org.stagemonitor.configuration.ConfigurationOption;
import org.stagemonitor.configuration.ConfigurationOptionProvider;

public class ServerlessConfiguration extends ConfigurationOptionProvider {
    public static final String SERVERLESS_CATEGORY = "Serverless";

    private final ConfigurationOption<String> awsLambdaHandler = ConfigurationOption.stringOption()
            .key("aws_lambda_handler")
            .tags("added[1.28.0]")
            .configurationCategory(SERVERLESS_CATEGORY)
            .description("This config option must be used when running the agent in an AWS Lambda context. \n" +
                    "This config value allows to specify the fully qualified name of the class handling the lambda function. \n" +
                    "An empty value (default value) indicates that the agent is not running within an AWS lambda function.")
            .buildWithDefault("");

    private final ConfigurationOption<Long> dataFlushTimeout = ConfigurationOption.longOption()
            .key("data_flush_timeout")
            .tags("added[1.28.0]")
            .configurationCategory(SERVERLESS_CATEGORY)
            .description("This config value allows to specify the timeout in milliseconds for flushing APM data at the end of a serverless function. \n" +
                    "For serverless functions, APM data is written in a synchronous way, thus, blocking the termination of the function util data is written or the specified timeout is reached.")
            .buildWithDefault(1000L);

    public String getAwsLambdaHandler() {
        return awsLambdaHandler.get();
    }

    public long getDataFlushTimeout() {
        return dataFlushTimeout.get();
    }

    public boolean runsOnAwsLambda() {
        String lambdaName = System.getenv("AWS_LAMBDA_FUNCTION_NAME");
        return null != lambdaName && !lambdaName.isEmpty();
    }
}
