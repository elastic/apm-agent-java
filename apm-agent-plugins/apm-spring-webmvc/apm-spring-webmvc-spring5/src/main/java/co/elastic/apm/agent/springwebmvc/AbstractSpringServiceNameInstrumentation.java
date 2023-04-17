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
import co.elastic.apm.agent.configuration.ServiceInfo;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.servlet.Constants;
import co.elastic.apm.agent.servlet.ServletServiceNameHelper;
import co.elastic.apm.agent.servlet.adapter.ServletContextAdapter;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.web.context.WebApplicationContext;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;

import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.nameEndsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public abstract class AbstractSpringServiceNameInstrumentation extends TracerAwareInstrumentation {

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameEndsWith("ApplicationContext");
    }

    public abstract Constants.ServletImpl servletImpl();

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return hasSuperType(
            named("org.springframework.web.context.WebApplicationContext")
                .and(declaresMethod(named("getServletContext").and(returns(servletImpl().servletContextClass()))))
        );
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("initPropertySources").and(takesArguments(0));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singletonList("spring-service-name");
    }

    public static class Helper {

        public static <ServletContext> void detectSpringServiceName(ServletContextAdapter<ServletContext> adapter,
                                                                    WebApplicationContext applicationContext, @Nullable ServletContext servletContext) {
            ElasticApmTracer elasticApmTracer = tracer.probe(ElasticApmTracer.class);
            if (elasticApmTracer == null) {
                return;
            }

            // avoid having two service names for a standalone jar
            // one based on Implementation-Title and one based on spring.application.name
            if (!ServiceInfo.autoDetected().isMultiServiceContainer()) {
                return;
            }

            // This method will be called whenever the spring application context is refreshed which may be more than once
            //
            // For example, using Tomcat Servlet container, it's called twice with the first not having a ServletContext,
            // while the second does, and later requests are initiated with the Servlet classloader and not the application
            // classloader.
            ClassLoader classLoader = applicationContext.getClassLoader();
            ServiceInfo fromServletContext = ServiceInfo.empty();
            if (servletContext != null) {
                ClassLoader servletClassloader = adapter.getClassLoader(servletContext);
                if (servletClassloader != null) {
                    classLoader = servletClassloader;
                    fromServletContext = ServletServiceNameHelper.detectServiceInfo(adapter, servletContext, servletClassloader);
                }
            }

            ServiceInfo fromSpringApplicationNameProperty = ServiceInfo.of(applicationContext.getEnvironment().getProperty("spring.application.name", ""));
            ServiceInfo fromApplicationContextApplicationName = ServiceInfo.of(removeLeadingSlash(applicationContext.getApplicationName()));

            elasticApmTracer.setServiceInfoForClassLoader(classLoader, fromSpringApplicationNameProperty
                .withFallback(fromServletContext)
                .withFallback(fromApplicationContextApplicationName));
        }

        private static String removeLeadingSlash(String appName) {
            // remove '/' (if any) from application name
            if (appName.startsWith("/")) {
                appName = appName.substring(1);
            }
            return appName;
        }
    }
}
