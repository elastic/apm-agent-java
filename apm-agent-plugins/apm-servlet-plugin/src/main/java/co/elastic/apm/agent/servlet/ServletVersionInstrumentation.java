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

import co.elastic.apm.agent.sdk.state.GlobalVariables;
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
import java.util.concurrent.atomic.AtomicBoolean;

import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.nameContainsIgnoreCase;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

/**
 * Instruments {@link javax.servlet.Servlet} to log Servlet container details and warns about unsupported version.
 */
public abstract class ServletVersionInstrumentation extends AbstractServletInstrumentation {

    private static final Logger logger = LoggerFactory.getLogger(ServletVersionInstrumentation.class);

    private static final AtomicBoolean alreadyLogged = GlobalVariables.get(ServletVersionInstrumentation.class, "alreadyLogged", new AtomicBoolean(false));

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
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        return any();
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

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        @SuppressWarnings("Duplicates") // duplication is fine here as it allows to inline code
        public static void onEnter(@Advice.Argument(0) @Nullable ServletConfig servletConfig) {
            logServletVersion(servletConfig);
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

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static void onEnter(@Advice.This Servlet servlet) {
            logServletVersion(servlet.getServletConfig());
        }
    }

    private static void logServletVersion(@Nullable ServletConfig servletConfig) {
        if (alreadyLogged.get()) {
            return;
        }
        alreadyLogged.set(true);

        int majorVersion = -1;
        int minorVersion = -1;
        String serverInfo = null;
        if (servletConfig != null) {
            ServletContext   servletContext = servletConfig.getServletContext();
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
