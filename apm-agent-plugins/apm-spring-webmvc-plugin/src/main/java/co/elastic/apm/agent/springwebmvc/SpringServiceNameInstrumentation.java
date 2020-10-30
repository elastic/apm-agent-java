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
package co.elastic.apm.agent.springwebmvc;

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.web.context.WebApplicationContext;

import java.util.Collection;
import java.util.Collections;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.nameEndsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class SpringServiceNameInstrumentation extends TracerAwareInstrumentation {

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameEndsWith("ApplicationContext");
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return hasSuperType(named("org.springframework.web.context.WebApplicationContext"));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("initPropertySources").and(takesArguments(0));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singletonList("spring-service-name");
    }

    @Override
    public Class<?> getAdviceClass() {
        return SpringServiceNameAdvice.class;
    }

    public static class SpringServiceNameAdvice {

        @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
        public static void afterInitPropertySources(@Advice.This WebApplicationContext applicationContext) {

            String appName = applicationContext.getEnvironment().getProperty("spring.application.name", "");

            // fallback when application name isn't set through an environment property
            if (appName.isEmpty()) {
                appName = applicationContext.getApplicationName();
                // remove '/' (if any) from application name
                if (appName.startsWith("/")) {
                    appName = appName.substring(1);
                }
            }

            tracer.overrideServiceNameForClassLoader(applicationContext.getClassLoader(), appName);
        }
    }
}
