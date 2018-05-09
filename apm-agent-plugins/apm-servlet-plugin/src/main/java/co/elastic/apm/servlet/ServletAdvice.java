/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 the original author or authors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.servlet;

import co.elastic.apm.bci.ElasticApmAdvice;
import co.elastic.apm.impl.ElasticApmTracer;
import co.elastic.apm.impl.transaction.Transaction;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

/**
 * Instruments traces calls to servlets.
 * <p>
 * This does actually not incorporate the time spent in {@link javax.servlet.Filter}s.
 * But registering a {@link javax.servlet.Filter} with BCI is difficult, error-prone and application server specific.
 * </p>
 */
public class ServletAdvice extends ElasticApmAdvice {

    @Nullable
    private static ApmFilter apmFilter;

    @Nullable

    @Advice.OnMethodEnter(inline = false)
    public static Transaction onEnterServletService() {
        if (apmFilter != null) {
            return apmFilter.onBefore();
        } else {
            return null;
        }
    }

    @Advice.OnMethodExit(inline = false, onThrowable = Exception.class)
    public static void onExitServletService(@Advice.Argument(0) HttpServletRequest request,
                                            @Advice.Argument(1) HttpServletResponse response,
                                            @Advice.Enter @Nullable Transaction transaction,
                                            @Advice.Thrown @Nullable Exception exception) {
        if (apmFilter != null && transaction != null) {
            apmFilter.onAfter(transaction, request, response, exception);
        }
    }

    @Override
    public void init(ElasticApmTracer tracer) {
        apmFilter = new ApmFilter(tracer);
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return not(isInterface())
            .and(nameContains("Servlet"))
            .and(hasSuperType(named("javax.servlet.http.HttpServlet")));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("service")
            .and(takesArgument(0, HttpServletRequest.class))
            .and(takesArgument(1, HttpServletResponse.class));
    }


}
