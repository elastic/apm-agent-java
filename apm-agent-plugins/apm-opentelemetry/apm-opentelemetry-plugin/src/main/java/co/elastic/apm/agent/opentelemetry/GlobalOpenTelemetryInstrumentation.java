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
import co.elastic.apm.agent.opentelemetry.global.ElasticOpenTelemetry;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Constructor;

import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * Instruments {@link GlobalOpenTelemetry#get()}
 */
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
    public String getAdviceClassName() {
        return "co.elastic.apm.agent.opentelemetry.GlobalOpenTelemetryInstrumentation$GlobalOpenTelemetryAdvice";
    }

    public static class GlobalOpenTelemetryAdvice {

        private static final Logger logger = LoggerFactory.getLogger(GlobalOpenTelemetryAdvice.class);

        @Nullable
        private static volatile OpenTelemetry elasticOpenTelemetry;

        @Advice.AssignReturned.ToReturned
        @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
        public static OpenTelemetry onExit(@Advice.Return OpenTelemetry originalOtel) {
            return getElasticOpenTelemetry(originalOtel);
        }

        @Nonnull
        private static OpenTelemetry buildOpenTelemetry(OpenTelemetry delegate) {
            ElasticApmTracer tracer = GlobalTracer.get().require(ElasticApmTracer.class);
            try {
                //Check if the provided Otel-API supports metrics
                OpenTelemetry.class.getMethod("getMeterProvider");
                //Lookup The with-metrics variant via reflection to guarantee it is only loaded if metrics are supported
                Class<?> clazz = Class.forName("co.elastic.apm.agent.opentelemetry.global.ElasticOpenTelemetryWithMetrics");
                Constructor<?> constructor = clazz.getConstructor(OpenTelemetry.class, ElasticApmTracer.class);
                return (OpenTelemetry) constructor.newInstance(delegate, tracer);
            } catch (Throwable e) {
                logger.debug("Falling back to OpenTelemetry without metrics", e);
                return new ElasticOpenTelemetry(tracer);
            }
        }

        private static OpenTelemetry getElasticOpenTelemetry(OpenTelemetry delegate) {
            if (elasticOpenTelemetry == null) {
                synchronized (GlobalOpenTelemetryAdvice.class) {
                    if (elasticOpenTelemetry == null) {
                        elasticOpenTelemetry = buildOpenTelemetry(delegate);
                    }
                }
            }
            return elasticOpenTelemetry;
        }

        /**
         * Must only be called from tests!
         * Allows tests to retrigger the creation of the elastic provided OpenTelemetry.
         * Currently invoked from OtelTestUtils.resetElasticOpenTelemetry() via reflection.
         */
        static void resetElasticOpenTelemetryForTests() {
            elasticOpenTelemetry = null;
        }
    }
}
