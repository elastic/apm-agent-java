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
package co.elastic.apm.agent.javalin;

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class JavalinHandlerLambdaInstrumentation extends TracerAwareInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("io.javalin.Javalin");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("addHandler")
            .and(takesArgument(0, named("io.javalin.http.HandlerType")))
            .and(takesArgument(1, String.class))
            .and(takesArgument(2, named("io.javalin.http.Handler")))
            // the fourth argument of Javalin.addHandler method is not matched, because its type differs between Javalin 3 and Javalin 4
            .and(takesArguments(4));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singleton("javalin");
    }

    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        return classLoaderCanLoadClass("io.javalin.http.Handler");
    }

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.javalin.JavalinHandlerLambdaInstrumentation$HandlerWrappingAdvice";
    }

    public static class HandlerWrappingAdvice {
        @Nullable
        @Advice.AssignReturned.ToArguments(@ToArgument(2))
        @Advice.OnMethodEnter(inline = false)
        public static Handler beforeAddHandler(@Advice.Argument(2) @Nullable Handler original) {
            if (original != null && original.getClass().getName().contains("/")) {
                return new WrappingHandler(original);
            }
            return original;
        }
    }

    static class WrappingHandler implements Handler {

        private final Handler wrappingHandler;

        public WrappingHandler(Handler wrappingHandler) {
            this.wrappingHandler = wrappingHandler;
        }

        @Override
        public void handle(@Nonnull Context ctx) throws Exception {
            wrappingHandler.handle(ctx);
        }
    }

}
