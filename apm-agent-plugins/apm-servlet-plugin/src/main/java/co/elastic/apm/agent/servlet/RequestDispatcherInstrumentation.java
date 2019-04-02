/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.agent.servlet;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Collection;

import static net.bytebuddy.matcher.ElementMatchers.*;

public class RequestDispatcherInstrumentation extends ElasticApmInstrumentation {

    @Override
    public Class<?> getAdviceClass() {
        return RequestDispatcherInstrumentation.RequestDispatcherAdvice.class;
    }

    public static class RequestDispatcherAdvice extends RequestDispatcherInstrumentation {
        @Advice.OnMethodEnter(suppress = Throwable.class)
        private static void beforeExecute(@Advice.Argument(0) @Nullable String path,
                                          @Advice.This @Nullable Object thiz) {
            if (thiz instanceof HttpServletRequest) {
                ((HttpServletRequest) thiz).setAttribute(RequestDispatcher.FORWARD_SERVLET_PATH, (path != null) ? path : "");
                ((HttpServletRequest) thiz).setAttribute(RequestDispatcher.INCLUDE_SERVLET_PATH, (path != null) ? path : "");
            }
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
        public static void afterExecute(@Advice.Thrown @Nullable Throwable t) {
        }
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return not(isInterface()).and(nameContainsIgnoreCase("Request"));
    }

    @Override
    public ElementMatcher<MethodDescription> getMethodMatcher() {
        return named("getRequestDispatcher").and(takesArgument(0, String.class));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("servlet", "request-dispatcher");
    }

}
