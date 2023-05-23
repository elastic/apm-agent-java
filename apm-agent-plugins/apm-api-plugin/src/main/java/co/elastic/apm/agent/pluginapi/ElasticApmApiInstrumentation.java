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
package co.elastic.apm.agent.pluginapi;

import co.elastic.apm.agent.configuration.ServiceInfo;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.Tracer;
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.tracer.Transaction;
import co.elastic.apm.agent.util.PrivilegedActionUtils;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.stagemonitor.configuration.ConfigurationOption;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;

import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * Injects the actual implementation of the public API class co.elastic.apm.api.ElasticApm.
 */
public class ElasticApmApiInstrumentation extends ApiInstrumentation {

    static final String PUBLIC_API_INSTRUMENTATION_GROUP = "public-api";
    private final ElementMatcher<? super MethodDescription> methodMatcher;

    ElasticApmApiInstrumentation(ElementMatcher<? super MethodDescription> methodMatcher) {
        this.methodMatcher = methodMatcher;
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("co.elastic.apm.api.ElasticApm");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return methodMatcher;
    }

    public static class StartTransactionInstrumentation extends ElasticApmApiInstrumentation {
        public StartTransactionInstrumentation() {
            super(named("doStartTransaction"));
        }

        public static class AdviceClass {
            @Nullable
            @Advice.AssignReturned.ToReturned
            @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
            public static Object doStartTransaction(@Advice.Origin Class<?> clazz) {
                Transaction<?> transaction = tracer.startRootTransaction(PrivilegedActionUtils.getClassLoader(clazz));
                if (transaction != null) {
                    transaction.setFrameworkName(Utils.FRAMEWORK_NAME);
                }
                return transaction;
            }
        }
    }

    public static class StartTransactionWithRemoteParentInstrumentation extends ElasticApmApiInstrumentation {

        public StartTransactionWithRemoteParentInstrumentation() {
            super(named("doStartTransactionWithRemoteParentFunction"));
        }

        public static class AdviceClass {
            @Nullable
            @Advice.AssignReturned.ToReturned
            @SuppressWarnings({"UnusedAssignment", "ParameterCanBeLocal", "unused"})
            @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
            public static Object doStartTransaction(@Advice.Origin Class<?> clazz,
                                                    @Advice.Argument(0) MethodHandle getFirstHeader,
                                                    @Advice.Argument(1) @Nullable Object headerExtractor,
                                                    @Advice.Argument(2) MethodHandle getAllHeaders,
                                                    @Advice.Argument(3) @Nullable Object headersExtractor) {
                Transaction<?> transaction = null;
                ClassLoader classLoader = PrivilegedActionUtils.getClassLoader(clazz);
                if (headersExtractor != null) {
                    HeadersExtractorBridge headersExtractorBridge = HeadersExtractorBridge.get(getFirstHeader, getAllHeaders);
                    transaction = tracer.startChildTransaction(HeadersExtractorBridge.Extractor.of(headerExtractor, headersExtractor), headersExtractorBridge, classLoader);
                } else if (headerExtractor != null) {
                    HeaderExtractorBridge headersExtractorBridge = HeaderExtractorBridge.get(getFirstHeader);
                    transaction = tracer.startChildTransaction(headerExtractor, headersExtractorBridge, classLoader);
                } else {
                    transaction = tracer.startRootTransaction(classLoader);
                }
                if (transaction != null) {
                    transaction.setFrameworkName(Utils.FRAMEWORK_NAME);
                }
                return transaction;
            }
        }
    }

    public static class CurrentTransactionInstrumentation extends ElasticApmApiInstrumentation {
        public CurrentTransactionInstrumentation() {
            super(named("doGetCurrentTransaction"));
        }

        public static class AdviceClass {
            @Nullable
            @Advice.AssignReturned.ToReturned
            @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
            public static Object doGetCurrentTransaction() {
                return tracer.currentTransaction();
            }
        }
    }

    public static class CurrentSpanInstrumentation extends ElasticApmApiInstrumentation {
        public CurrentSpanInstrumentation() {
            super(named("doGetCurrentSpan"));
        }

        public static class AdviceClass {
            @Nullable
            @Advice.AssignReturned.ToReturned
            @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
            public static Object doGetCurrentSpan() {
                return tracer.getActive();
            }
        }
    }

    public static class CaptureExceptionInstrumentation extends ElasticApmApiInstrumentation {
        public CaptureExceptionInstrumentation() {
            super(named("captureException"));
        }

        public static class AdviceClass {
            @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
            public static void captureException(@Advice.Origin Class<?> clazz, @Advice.Argument(0) @Nullable Throwable e) {
                tracer.require(Tracer.class).captureAndReportException(e, PrivilegedActionUtils.getClassLoader(clazz));
            }
        }
    }

    public static class SetServiceInfoForClassLoaderInstrumentation extends ElasticApmApiInstrumentation {
        public SetServiceInfoForClassLoaderInstrumentation() {
            super(named("setServiceInfoForClassLoader"));
        }

        public static class AdviceClass {
            @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
            public static void setServiceInfoForClassLoader(@Advice.Argument(0) @Nullable ClassLoader classLoader, @Advice.Argument(1) String serviceName, @Advice.Argument(2) @Nullable String serviceVersion) {
                tracer.require(Tracer.class).setServiceInfoForClassLoader(classLoader, ServiceInfo.of(serviceName, serviceVersion));
            }
        }
    }

    public static class ConfigInstrumentation extends ElasticApmApiInstrumentation {
        public ConfigInstrumentation() {
            super(named("doGetConfig"));
        }

        public static class AdviceClass {
            @Nullable
            @Advice.AssignReturned.ToReturned
            @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
            public static Object doGetConfig(@Advice.Argument(0) @Nullable String key) {
                try {
                    ConfigurationOption<?> configValue = GlobalTracer.get()
                        .require(ElasticApmTracer.class)
                        .getConfigurationRegistry()
                        .getConfigurationOptionByKey(key);
                    if (configValue == null) {
                        return null;
                    } else {
                        return configValue.getValue();
                    }
                } catch (NullPointerException e) {
                    //this can only happen if the agent is messed up or not yet initialized
                    return new IllegalStateException("The agent is not initialized");
                }
            }
        }
    }
}
