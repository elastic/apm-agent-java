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
package co.elastic.apm.agent.opentelemetry;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.opentelemetry.tracing.OTelBridgeContext;
import io.opentelemetry.context.Context;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesNoArguments;

/**
 * Instruments {@code io.opentelemetry.context.ArrayBasedContext#root()} to capture original context root
 * and allows relying on the provided context implementation for key/value storage in context
 */
public class ArrayBasedContextInstrumentation extends AbstractOpenTelemetryInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("io.opentelemetry.context.ArrayBasedContext");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("root")
            .and(isStatic())
            .and(returns(hasSuperType(named("io.opentelemetry.context.Context"))))
            .and(takesNoArguments());
    }

    @Override
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.opentelemetry.ArrayBasedContextInstrumentation$RootAdvice";
    }

    public static class RootAdvice {

        @Nullable
        @Advice.AssignReturned.ToReturned
        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static Context onExit(@Advice.Return @Nullable Context returnValue) {

            if (returnValue == null) {
                return null;
            }

            return OTelBridgeContext.bridgeRootContext(GlobalTracer.get().require(ElasticApmTracer.class), returnValue);
        }
    }
}
