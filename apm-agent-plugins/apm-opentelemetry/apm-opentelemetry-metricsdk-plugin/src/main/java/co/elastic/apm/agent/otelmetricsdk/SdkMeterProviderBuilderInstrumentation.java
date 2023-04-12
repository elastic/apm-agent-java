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
package co.elastic.apm.agent.otelmetricsdk;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.sdk.ElasticApmInstrumentation;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakConcurrent;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakSet;
import co.elastic.apm.agent.util.LoggerUtils;
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.Arrays;
import java.util.Collection;

import static net.bytebuddy.matcher.ElementMatchers.named;

public class SdkMeterProviderBuilderInstrumentation extends ElasticApmInstrumentation {
    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("build");
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        //These groups need to be kept in sync with the if-condition in ElasticOpenTelemetryWithMetrics
        return Arrays.asList("opentelemetry", "opentelemetry-metrics", "experimental");
    }

    @Override
    public String getAdviceClassName() {
        return getClass().getName() + "$SdkMeterProviderBuilderAdvice";
    }

    /**
     * Instruments {@link SdkMeterProviderBuilder#build()}.
     */
    public static class SdkMeterProviderBuilderAdvice {

        private static final Logger logger = LoggerFactory.getLogger(SdkMeterProviderBuilderInstrumentation.SdkMeterProviderBuilderAdvice.class);
        private static final Logger unsupportedVersionLogger = LoggerUtils.logOnce(logger);

        private static final WeakSet<SdkMeterProviderBuilder> ALREADY_REGISTERED_BUILDERS = WeakConcurrent.buildSet();
        private static final ElasticApmTracer tracer = GlobalTracer.get().require(ElasticApmTracer.class);

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static void onEnter(@Advice.This SdkMeterProviderBuilder thiz) {
            if (checkMetricsSdkVersionSupported()) {
                if (ALREADY_REGISTERED_BUILDERS.add(thiz)) {
                    ElasticOtelMetricsExporter.createAndRegisterOn(thiz, tracer);
                }
            }
        }

        private static boolean checkMetricsSdkVersionSupported() {
            // pre 1.16.0 versions did either not include the MetricsExporter class
            // or the exporter does not support configuring aggregations
            try {
                Class<?> instrumentTypeClass = Class.forName("io.opentelemetry.sdk.metrics.InstrumentType");
                MetricExporter.class.getMethod("getDefaultAggregation", instrumentTypeClass);
                return true;
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                unsupportedVersionLogger.warn("Detected OpenTelemetry metrics SDK instance with a version older than 1.16.0. Skipping instrumentation because it is not supported.");
                return false;
            }
        }

    }

}
