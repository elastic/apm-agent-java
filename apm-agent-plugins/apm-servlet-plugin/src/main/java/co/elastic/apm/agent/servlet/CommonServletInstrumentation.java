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

import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.Arrays;
import java.util.Collection;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.nameContainsIgnoreCase;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

/**
 * Instruments servlets to create transactions.
 * <p>
 * If the transaction has already been recorded with the help of {@link FilterChainInstrumentation},
 * it does not record the transaction again.
 * But if there is no filter registered for a servlet,
 * this makes sure to record a transaction in that case.
 * </p>
 */
public abstract class CommonServletInstrumentation extends CommonAbstractServletInstrumentation {

    static final String SERVLET_API = "servlet-api";
    static final String SERVLET_API_DISPATCH = "servlet-api-dispatch";

    public CommonServletInstrumentation(InstrumentationClassHelper classHelper) {
        super(classHelper);
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList(SERVLET_API, SERVLET_API_DISPATCH);
    }

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameContains("Servlet").or(nameContainsIgnoreCase("jsp"));
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return not(isInterface())
            .and(hasSuperType(named(instrumentationClassHelper.servletClassName())));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("service")
            .and(takesArgument(0, named(instrumentationClassHelper.servletRequestClassName())))
            .and(takesArgument(1, named(instrumentationClassHelper.servletResponseClassName())));
    }

    @Override
    public String getAdviceClassName() {
        return instrumentationClassHelper.servletApiAdviceClassName();
    }

}
