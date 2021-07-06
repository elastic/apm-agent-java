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
package co.elastic.apm.agent.httpserver;

import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpHandler;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

/**
 * Instruments
 * <ul>
 *     <li>{@link com.sun.net.httpserver.HttpServer#createContext(String)}</li>
 *     <li>{@link com.sun.net.httpserver.HttpServer#createContext(String, HttpHandler)} )}</li>
 * </ul>
 */
public class HttpServerInstrumentation extends JdkHttpServerInstrumentation {

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameStartsWith("sun.net.httpserver") // implementation is in sun.net
            .and(nameContains("ServerImpl"));
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return hasSuperType(named("com.sun.net.httpserver.HttpServer"));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("createContext")
            .and(returns(hasSuperType(named("com.sun.net.httpserver.HttpContext"))));
    }

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.httpserver.HttpServerInstrumentation$HttpServerAdvice";
    }

    public static class HttpServerAdvice {

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void onExit(@Advice.Return @Nullable HttpContext returnValue) {
            if (returnValue == null) {
                return;
            }

            // handler might be null here, in which case it will be set on context
            HttpHandlerHelper.ensureInstrumented(returnValue.getHandler());
        }
    }
}
