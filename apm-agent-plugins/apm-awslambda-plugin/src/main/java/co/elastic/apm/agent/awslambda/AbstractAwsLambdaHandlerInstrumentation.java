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
package co.elastic.apm.agent.awslambda;

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.configuration.ServerlessConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.none;

public abstract class AbstractAwsLambdaHandlerInstrumentation extends TracerAwareInstrumentation {

    protected final ServerlessConfiguration serverlessConfiguration;

    @Nullable
    protected String handlerClassName;

    @Nullable
    protected String handlerMethodName;

    public AbstractAwsLambdaHandlerInstrumentation(ElasticApmTracer tracer) {
        serverlessConfiguration = tracer.getConfig(ServerlessConfiguration.class);
        String awsLambdaHandler = serverlessConfiguration.getAwsLambdaHandler();
        //noinspection ConstantConditions
        if (awsLambdaHandler != null && !awsLambdaHandler.isEmpty()) {
            String[] handlerConfigParts = awsLambdaHandler.split("::");
            handlerClassName = handlerConfigParts[0];
            if (handlerConfigParts.length > 1) {
                handlerMethodName = handlerConfigParts[1];
            }
        }
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singleton("aws-lambda");
    }

    /**
     * Matches either custom types or implementations of {@link com.amazonaws.services.lambda.runtime.RequestHandler} or
     * {@link com.amazonaws.services.lambda.runtime.RequestStreamHandler}.
     * The actual instrumentation will be made based on method matching.
     * @return handler type matching based on the configured {@link ServerlessConfiguration#awsLambdaHandler}
     */
    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        if (handlerClassName == null) {
            return none();
        }
        return named(handlerClassName);
    }
}
