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
package co.elastic.apm.agent.springwebmvc;

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import org.springframework.web.context.WebApplicationContext;

import javax.servlet.ServletContext;
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
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.springwebmvc.SpringServiceNameInstrumentation$SpringServiceNameAdvice";
    }

    public static class SpringServiceNameAdvice {

        private static final Logger logger = LoggerFactory.getLogger(SpringServiceNameAdvice.class);

        @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
        public static void afterInitPropertySources(@Advice.This WebApplicationContext applicationContext) {
            // This method will be called whenever the spring application context is refreshed which may be more than once
            //
            // For example, using Tomcat Servlet container, it's called twice with the first not having a ServletContext,
            // while the second does, and later requests are initiated with the Servlet classloader and not the application
            // classloader.
            ClassLoader classLoader = applicationContext.getClassLoader();

            ServletContext servletContext = applicationContext.getServletContext();
            if (servletContext != null) {
                try {
                    ClassLoader servletClassloader = servletContext.getClassLoader();
                    if (servletClassloader != null) {
                        classLoader = servletClassloader;
                    }
                } catch (UnsupportedOperationException e) {
                    // silently ignored
                }
            }

            String appName = applicationContext.getEnvironment().getProperty("spring.application.name", "");

            if (!appName.isEmpty()) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Setting service name `{}` to be used for class loader [{}], based on the value of " +
                        "the `spring.application.name` environment variable", appName, classLoader);
                }
            } else {
                // fallback when application name isn't set through an environment property
                appName = applicationContext.getApplicationName();
                // remove '/' (if any) from application name
                if (appName.startsWith("/")) {
                    appName = appName.substring(1);
                }
                if (logger.isDebugEnabled()) {
                    logger.debug("``spring.application.name` environment variable is not set, falling back to using `{}` " +
                        "as service name for class loader [{}]", appName, classLoader);
                }
            }

            tracer.overrideServiceNameForClassLoader(classLoader, appName);
        }
    }
}
