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
package co.elastic.apm.agent.servlet;

import co.elastic.apm.agent.sdk.state.GlobalVariables;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicBoolean;

import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.nameContainsIgnoreCase;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

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
            .and(hasSuperType(named(servletVersionTypeMatcherClassName())));
    }

    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        return any();
    }

    public abstract String servletVersionTypeMatcherClassName();

    public static abstract class Init extends ServletVersionInstrumentation {

        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            return named("init")
                .and(takesArgument(0, named(initMethodArgumentClassName())));
        }

        abstract String initMethodArgumentClassName();
    }

    public static abstract class Service extends ServletVersionInstrumentation {
        @Override
        public ElementMatcher<? super MethodDescription> getMethodMatcher() {
            String[] classNames = getServiceMethodArgumentClassNames();
            return named("service")
                .and(takesArgument(0, named(classNames[0])))
                .and(takesArgument(1, named(classNames[1])));
        }

        abstract String[] getServiceMethodArgumentClassNames();
    }

    public static void logServletVersion(@Nullable Object[] infoFromServletContext) {
        if (alreadyLogged.get()) {
            return;
        }
        alreadyLogged.set(true);

        int majorVersion = -1;
        int minorVersion = -1;
        String serverInfo = null;
        if (infoFromServletContext != null && infoFromServletContext.length > 2) {
            if (infoFromServletContext[0] != null) {
                majorVersion = (int) infoFromServletContext[0];
            }
            if (infoFromServletContext[1] != null) {
                minorVersion = (int) infoFromServletContext[1];
            }
            if (infoFromServletContext[2] instanceof String) {
                serverInfo = (String) infoFromServletContext[2];
            }
        }
        logger.info("Servlet container info = {}", serverInfo);
        if (majorVersion < 3) {
            logger.warn("Unsupported servlet version detected: {}.{}, no Servlet transaction will be created", majorVersion, minorVersion);
        }
    }

}
