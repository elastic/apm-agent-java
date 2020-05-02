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
package co.elastic.apm.agent.grails;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.impl.transaction.Transaction;
import grails.core.GrailsControllerClass;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.grails.web.mapping.mvc.GrailsControllerUrlMappingInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;
import static co.elastic.apm.agent.impl.transaction.AbstractSpan.PRIO_HIGH_LEVEL_FRAMEWORK;
import static grails.core.GrailsControllerClass.INDEX_ACTION;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public class GrailsTransactionNameInstrumentation extends ElasticApmInstrumentation {

    @VisibleForAdvice
    @SuppressWarnings("WeakerAccess")
    public static final Logger logger = LoggerFactory.getLogger(GrailsTransactionNameInstrumentation.class);

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return nameStartsWith("org.grails.web.mapping.mvc")
            .and(hasSuperType(named("org.springframework.web.servlet.HandlerAdapter")))
            .and(not(isInterface()));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("handle")
            .and(returns(named("org.springframework.web.servlet.ModelAndView")))
            .and(takesArgument(0, named("javax.servlet.http.HttpServletRequest")))
            .and(takesArgument(1, named("javax.servlet.http.HttpServletResponse")))
            .and(takesArgument(2, Object.class));
    }

    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        return classLoaderCanLoadClass("org.grails.web.servlet.mvc.GrailsDispatcherServlet");
    }

    @Override
    public Class<?> getAdviceClass() {
        return HandlerAdapterAdvice.class;
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singletonList("grails");
    }

    @VisibleForAdvice
    public static class HandlerAdapterAdvice {

        @Advice.OnMethodEnter(suppress = Throwable.class)
        static void setTransactionName(@Advice.Argument(2) Object handler) {
            if (tracer != null) {
                final Transaction transaction = tracer.currentTransaction();
                if (transaction != null) {
                    final String className;
                    final String methodName;
                    if (handler instanceof GrailsControllerUrlMappingInfo) {
                        GrailsControllerUrlMappingInfo urlMappingInfo = ((GrailsControllerUrlMappingInfo) handler);
                        GrailsControllerClass grailsControllerClass = urlMappingInfo.getControllerClass();
                        className = grailsControllerClass.getShortName();
                        String actionName = urlMappingInfo.getActionName();
                        methodName = actionName != null ? actionName : INDEX_ACTION;
                    } else {
                        className = handler.getClass().getSimpleName();
                        methodName = null;
                    }
                    setName(transaction, className, methodName);
                    if (logger.isDebugEnabled()) {
                        logger.debug("Set name {} to transaction {}", transaction.getNameAsString(), transaction.getTraceContext().getId());
                    }
                } else {
                    logger.debug("Transaction is null");
                }
            } else {
                logger.debug("Tracer is null");
            }
            if (logger.isTraceEnabled()) {
                logger.trace("Stack trace: ", new RuntimeException());
            }
        }

        @VisibleForAdvice
        public static void setName(Transaction transaction, String className, @Nullable String methodName) {
            final StringBuilder name = transaction.getAndOverrideName(PRIO_HIGH_LEVEL_FRAMEWORK);
            if (name != null) {
                name.append(className);
                if (methodName != null) {
                    name.append('#').append(methodName);
                }
            }
        }
    }
}
