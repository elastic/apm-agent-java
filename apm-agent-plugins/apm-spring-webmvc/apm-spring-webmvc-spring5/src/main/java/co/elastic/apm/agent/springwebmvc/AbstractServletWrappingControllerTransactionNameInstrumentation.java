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
import co.elastic.apm.agent.servlet.Constants;
import co.elastic.apm.agent.servlet.adapter.ServletRequestAdapter;
import co.elastic.apm.agent.tracer.Transaction;
import co.elastic.apm.agent.util.TransactionNameUtils;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.Collection;
import java.util.Collections;

import static co.elastic.apm.agent.tracer.AbstractSpan.PRIORITY_HIGH_LEVEL_FRAMEWORK;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

/**
 * Instruments {@link org.springframework.web.servlet.mvc.ServletWrappingController#handleRequestInternal} and sets the transaction name
 * to the name of the servlet,
 * overriding the transaction name set by {@link AbstractSpringTransactionNameInstrumentation} that would be {@code ServletWrappingController}.
 */
public abstract class AbstractServletWrappingControllerTransactionNameInstrumentation extends TracerAwareInstrumentation {

    public abstract Constants.ServletImpl servletImpl();

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("org.springframework.web.servlet.mvc.ServletWrappingController");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("handleRequestInternal").and(takesArgument(0, servletImpl().httpRequestClassMatcher()));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singletonList("spring-mvc");
    }

    public static class Helper {

        public static <HttpServletRequest> void updateTransactionNameFromRequest(ServletRequestAdapter<HttpServletRequest, ?> adapter, Class<?> servletClass, HttpServletRequest request) {
            final Transaction<?> transaction = tracer.currentTransaction();
            if (transaction == null) {
                return;
            }
            TransactionNameUtils.setTransactionNameByServletClass(adapter.getMethod(request), servletClass, transaction.getAndOverrideName(PRIORITY_HIGH_LEVEL_FRAMEWORK));
        }
    }

}
