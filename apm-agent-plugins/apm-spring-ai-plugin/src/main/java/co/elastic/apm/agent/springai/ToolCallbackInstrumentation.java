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
package co.elastic.apm.agent.springai;

import co.elastic.apm.agent.tracer.Span;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.ai.tool.ToolCallback;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.*;

public class ToolCallbackInstrumentation extends AbstractSpringAiInstrumentation {

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameContains("Tool").or(nameContains("Callback"));
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return not(isInterface()).and(hasSuperType(named("org.springframework.ai.tool.ToolCallback")));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("call").and(takesArguments(String.class));
    }

    @Override
    public String getAdviceClassName() {
        return getClass().getName() + "$ToolCallbackAdvice";
    }

    public static class ToolCallbackAdvice {
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object onEnter(@Advice.This ToolCallback callback) {
            Span<?> span = SpringAiSpanHelper.startToolSpan(callback);
            return span == null ? null : span.activate();
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void onExit(@Advice.Enter @Nullable Object spanObject,
                                  @Advice.Thrown @Nullable Throwable thrown) {
            SpringAiSpanHelper.endSpan((Span<?>) spanObject, thrown);
        }
    }
}
