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
package co.elastic.apm.agent.grails;

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.tracer.Transaction;
import co.elastic.apm.agent.util.TransactionNameUtils;
import grails.core.GrailsControllerClass;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.grails.web.mapping.mvc.GrailsControllerUrlMappingInfo;

import java.util.Collection;
import java.util.Collections;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;
import static co.elastic.apm.agent.tracer.AbstractSpan.PRIORITY_HIGH_LEVEL_FRAMEWORK;
import static grails.core.GrailsControllerClass.INDEX_ACTION;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public class GrailsTransactionNameInstrumentation extends TracerAwareInstrumentation {

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

    /**
     * Excludes Grails 2
     */
    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        return classLoaderCanLoadClass("org.grails.web.mapping.mvc.GrailsControllerUrlMappingInfo");
    }

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.grails.GrailsTransactionNameInstrumentation$HandlerAdapterAdvice";
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singletonList("grails");
    }

    public static class HandlerAdapterAdvice {

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static void setTransactionName(@Advice.Argument(2) Object handler) {
            final Transaction<?> transaction = tracer.currentTransaction();
            if (transaction == null) {
                return;
            }
            final String className;
            final String methodName;
            if (handler instanceof GrailsControllerUrlMappingInfo) {
                GrailsControllerUrlMappingInfo urlMappingInfo = (GrailsControllerUrlMappingInfo) handler;
                GrailsControllerClass grailsControllerClass = urlMappingInfo.getControllerClass();
                className = grailsControllerClass.getShortName();
                String actionName = urlMappingInfo.getActionName();
                methodName = actionName != null ? actionName : INDEX_ACTION;
            } else {
                className = handler.getClass().getSimpleName();
                methodName = null;
            }
            TransactionNameUtils.setNameFromClassAndMethod(className, methodName, transaction.getAndOverrideName(PRIORITY_HIGH_LEVEL_FRAMEWORK));
        }
    }
}
