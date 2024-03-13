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
package co.elastic.apm.agent.opentelemetry;

import co.elastic.apm.agent.sdk.ElasticApmInstrumentation;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.Collection;
import java.util.Collections;

import static co.elastic.apm.agent.sdk.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;

public abstract class AbstractOpenTelemetryInstrumentation extends ElasticApmInstrumentation {

    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        return classLoaderCanLoadClass("io.opentelemetry.context.propagation.TextMapSetter")
            //TracerBuilder has been added in 1.4.0, which is the minimum supported API version
            .and(classLoaderCanLoadClass("io.opentelemetry.api.trace.TracerBuilder"));
    }

    @Override
    public final boolean includeWhenInstrumentationIsDisabled() {
        return true;
    }


    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singletonList("opentelemetry");
    }
}
