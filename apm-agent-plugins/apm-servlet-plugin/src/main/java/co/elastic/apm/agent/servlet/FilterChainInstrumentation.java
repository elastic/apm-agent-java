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

import co.elastic.apm.agent.bootstrap.MethodHandleDispatcher;
import co.elastic.apm.agent.impl.transaction.Transaction;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

/**
 * Instruments {@link javax.servlet.FilterChain}s to create transactions.
 */
public class FilterChainInstrumentation extends AbstractServletInstrumentation {

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameContains("Chain");
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return not(isInterface())
            .and(hasSuperType(named("javax.servlet.FilterChain")));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("doFilter")
            .and(takesArgument(0, named("javax.servlet.ServletRequest")))
            .and(takesArgument(1, named("javax.servlet.ServletResponse")));
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    private static Transaction onEnterServletService(@Advice.Origin Class<?> clazz,
                                                     @Advice.Argument(0) ServletRequest servletRequest) throws Throwable {
        return (Transaction) MethodHandleDispatcher
            .getMethodHandle(clazz, "co.elastic.apm.agent.servlet.ServletApiAdvice#onEnterServletService")
            .invoke(servletRequest);
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    private static void onExitServletService(@Advice.Origin Class<?> clazz,
                                             @Advice.Argument(0) ServletRequest servletRequest,
                                             @Advice.Argument(1) ServletResponse servletResponse,
                                             @Advice.Enter @Nullable Transaction transaction,
                                             @Advice.Thrown @Nullable Throwable t,
                                             @Advice.This Object thiz) throws Throwable {
        MethodHandleDispatcher
            .getMethodHandle(clazz, "co.elastic.apm.agent.servlet.ServletApiAdvice#onExitServletService")
            .invoke(servletRequest, servletResponse, transaction, t, thiz);
    }

}
