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
package co.elastic.apm.agent.servlet;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.bci.RegisterMethodHandle;
import co.elastic.apm.agent.bootstrap.MethodHandleDispatcher;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.util.Collection;
import java.util.Collections;

import static co.elastic.apm.agent.servlet.ServletInstrumentation.SERVLET_API;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.nameContainsIgnoreCase;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

/**
 * Instruments {@link javax.servlet.Servlet} to log Servlet container details and warns about unsupported version.
 * <p>
 * Does not inherit from {@link AbstractServletInstrumentation} in order to still instrument when servlet version is not
 * supported.
 */
public abstract class ServletVersionInstrumentation extends ElasticApmInstrumentation {

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameContains("Servlet").or(nameContainsIgnoreCase("jsp"));
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return not(isInterface())
            .and(hasSuperType(named("javax.servlet.Servlet")));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singleton(SERVLET_API);
    }

    /**
     * Instruments {@link javax.servlet.Servlet#init(ServletConfig)}
     */
    public static class Init extends ServletVersionInstrumentation {

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("init")
                .and(takesArgument(0, named("javax.servlet.ServletConfig")));
        }

        @Advice.OnMethodEnter(suppress = Throwable.class)
        @SuppressWarnings("Duplicates") // duplication is fine here as it allows to inline code
        private static void onEnter(@Advice.Origin Class<?> clazz, @Advice.Argument(0) @Nullable ServletConfig servletConfig) throws Throwable {
            if (MethodHandleDispatcher.USE_REFLECTION) {
                MethodHandleDispatcher
                    .getMethod(clazz, "co.elastic.apm.agent.servlet.ServletVersionInstrumentation$ServletVersionHelper#warnIfVersionNotSupportedServletConfig")
                    .invoke(null, servletConfig);
            } else {
                MethodHandleDispatcher
                    .getMethodHandle(clazz, "co.elastic.apm.agent.servlet.ServletVersionInstrumentation$ServletVersionHelper#warnIfVersionNotSupportedServletConfig")
                    .invoke(servletConfig);
            }
        }
    }

    /**
     * Instruments {@link javax.servlet.Servlet#service(ServletRequest, ServletResponse)}
     */
    public static class Service extends ServletVersionInstrumentation {

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("service")
                .and(takesArgument(0, named("javax.servlet.ServletRequest")))
                .and(takesArgument(1, named("javax.servlet.ServletResponse")));
        }

        @Advice.OnMethodEnter(suppress = Throwable.class)
        @SuppressWarnings("Duplicates") // duplication is fine here as it allows to inline code
        private static void onEnter(@Advice.Origin Class<?> clazz, @Advice.This Servlet servlet) throws Throwable {
            if (MethodHandleDispatcher.USE_REFLECTION) {
                MethodHandleDispatcher
                    .getMethod(clazz, "co.elastic.apm.agent.servlet.ServletVersionInstrumentation$ServletVersionHelper#warnIfVersionNotSupportedServlet")
                    .invoke(null, servlet);
            } else {
                MethodHandleDispatcher
                    .getMethodHandle(clazz, "co.elastic.apm.agent.servlet.ServletVersionInstrumentation$ServletVersionHelper#warnIfVersionNotSupportedServlet")
                    .invoke(servlet);
            }
        }
    }

    public static class ServletVersionHelper {

        private static final Logger logger = LoggerFactory.getLogger(ServletVersionInstrumentation.class);

        private static volatile boolean alreadyLogged = false;

        @RegisterMethodHandle
        public static void warnIfVersionNotSupportedServlet(Servlet servlet) {
            warnIfVersionNotSupportedServletConfig(servlet.getServletConfig());
        }

        @RegisterMethodHandle
        public static void warnIfVersionNotSupportedServletConfig(@Nullable ServletConfig servletConfig) {
            if (alreadyLogged) {
                return;
            }
            alreadyLogged = true;

            int majorVersion = -1;
            int minorVersion = -1;
            String serverInfo = null;
            if (servletConfig != null) {
                ServletContext servletContext = servletConfig.getServletContext();
                if (null != servletContext) {
                    majorVersion = servletContext.getMajorVersion();
                    minorVersion = servletContext.getMinorVersion();
                    serverInfo = servletContext.getServerInfo();
                }
            }

            logger.info("Servlet container info = {}", serverInfo);
            if (majorVersion < 3) {
                logger.warn("Unsupported servlet version detected: {}.{}, no Servlet transaction will be created", majorVersion, minorVersion);
            }
        }
    }


}
