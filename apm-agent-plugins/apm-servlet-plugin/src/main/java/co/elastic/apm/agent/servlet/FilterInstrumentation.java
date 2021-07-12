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

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

/**
 * Instruments servlets to create transactions.
 * <p>
 * If the transaction has already been recorded with the help of {@link CommonFilterChainInstrumentation},
 * it does not record the transaction again.
 * But if there is no filter registered for a servlet,
 * this makes sure to record a transaction in that case.
 * </p>
 */
public abstract class CommonFilterInstrumentation extends AbstractServletInstrumentation {

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameContains("Filter");
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return not(isInterface())
            .and(hasSuperType(named(getFilterClassName())));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        String[] classNames = getServletMethodArgumentNames();
        return named("doFilter")
            .and(takesArgument(0, named(classNames[0])))
            .and(takesArgument(1, named(classNames[1])));
    }

    public abstract String getFilterClassName();

    public abstract String[] getServletMethodArgumentNames();

}
