/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
 * %%
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
 * #L%
 */
package co.elastic.apm.agent.indymodule;

import co.elastic.apm.agent.sdk.ElasticApmInstrumentation;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

import java.util.Collection;
import java.util.Collections;

import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * NOTE: THIS CANNOT BE AN INDY PLUGIN, AS IT IS THE INSTRUMENTATION THAT FACILITATES INDY PLUGINS FUNCTIONALITY
 */
public class ClassModuleInstrumentation extends ElasticApmInstrumentation {

    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        return ElementMatchers.isBootstrapClassLoader();
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("java.lang.Class");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return ElementMatchers.named("getModule").and(ElementMatchers.returns(ElementMatchers.named("java.lang.Module")));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singletonList("java-base");
    }

    @Override
    public Class<?> getAdviceClass() {
        return GetModuleAdvice.class;
    }
}
