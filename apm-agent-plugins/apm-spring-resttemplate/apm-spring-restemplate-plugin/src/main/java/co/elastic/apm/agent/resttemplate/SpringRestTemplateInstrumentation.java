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
package co.elastic.apm.agent.resttemplate;

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.Arrays;
import java.util.Collection;

import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class SpringRestTemplateInstrumentation extends TracerAwareInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return nameStartsWith("org.springframework")
            .and(not(isInterface()))
            // only traverse the object hierarchy if the class declares the method to instrument at all
            .and(declaresMethod(getMethodMatcher()))
            .and(hasSuperType(named("org.springframework.http.client.ClientHttpRequest")
                .and(// only instrument spring 3.0.3+ getURI method is misssing previously
                    declaresMethod(named("getURI").and(takesArguments(0)).and(returns(named("java.net.URI"))))
                ).or(
                    // for spring 3.1.0+ HttpRequest type has getURI method
                    hasSuperType(named("org.springframework.http.HttpRequest")))));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("execute")
            .and(takesArguments(0))
            .and(returns(hasSuperType(named("org.springframework.http.client.ClientHttpResponse"))));
    }

    @Override
    public Class<?> getAdviceClass() {
        return SpringRestTemplateAdvice.class;
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("http-client", "spring-resttemplate");
    }
}
