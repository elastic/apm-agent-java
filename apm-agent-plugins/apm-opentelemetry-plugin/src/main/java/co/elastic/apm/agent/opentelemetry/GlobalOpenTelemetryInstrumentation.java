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
package co.elastic.apm.agent.opentelemetry;

import co.elastic.apm.agent.impl.GlobalTracer;
import co.elastic.apm.agent.opentelemetry.sdk.ElasticOTelTracer;
import co.elastic.apm.agent.opentelemetry.sdk.ElasticOTelTracerProvider;
import co.elastic.apm.agent.sdk.advice.AssignTo;
import io.opentelemetry.api.DefaultOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.matcher.ElementMatchers.named;

public class GlobalOpenTelemetryInstrumentation extends AbstractOpenTelemetryInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("io.opentelemetry.api.GlobalOpenTelemetry");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("get");
    }

    @Override
    public Class<?> getAdviceClass() {
        return GlobalOpenTelemetryAdvice.class;
    }

    public static class GlobalOpenTelemetryAdvice {

        private static final OpenTelemetry ELASTIC_OPEN_TELEMETRY = DefaultOpenTelemetry.builder()
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .setTracerProvider(new ElasticOTelTracerProvider(new ElasticOTelTracer(GlobalTracer.requireTracerImpl())))
            .build();

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false, skipOn = Advice.OnNonDefaultValue.class)
        public static boolean onEnter() {
            // skips actual method and directly goes to exit advice
            return true;
        }

        @AssignTo.Return
        @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
        public static OpenTelemetry onExit() {
            return ELASTIC_OPEN_TELEMETRY;
        }
    }
}
