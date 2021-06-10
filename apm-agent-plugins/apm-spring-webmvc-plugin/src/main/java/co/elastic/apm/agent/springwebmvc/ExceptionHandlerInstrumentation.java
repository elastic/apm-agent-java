/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2021 Elastic and contributors
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
package co.elastic.apm.agent.springwebmvc;

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Collection;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public class ExceptionHandlerInstrumentation extends TracerAwareInstrumentation {

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.springwebmvc.ExceptionHandlerInstrumentation$ExceptionHandlerAdviceService";
    }

    public static class ExceptionHandlerAdviceService {

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static void captureException(@Advice.Argument(0) @Nullable HttpServletRequest request,
                                            @Advice.Argument(3) @Nullable Exception e) {
            if (request != null && e != null) {
                request.setAttribute("co.elastic.apm.exception", e);
            }
        }
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("org.springframework.web.servlet.DispatcherServlet");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("processHandlerException")
            .and(takesArgument(0, named("javax.servlet.http.HttpServletRequest")))
            .and(takesArgument(1, named("javax.servlet.http.HttpServletResponse")))
            .and(takesArgument(2, named("java.lang.Object")))
            .and(takesArgument(3, named("java.lang.Exception")))
            .and(returns(named("org.springframework.web.servlet.ModelAndView")));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("exception-handler");
    }
}
