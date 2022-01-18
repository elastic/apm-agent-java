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

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.bci.bytebuddy.AnnotationValueOffsetMappingFactory.AnnotationValueExtractor;
import co.elastic.apm.agent.bci.bytebuddy.SimpleMethodSignatureOffsetMappingFactory.SimpleMethodSignature;
import co.elastic.apm.agent.configuration.CoreConfiguration;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.agent.impl.transaction.Outcome;
import co.elastic.apm.agent.impl.transaction.Transaction;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import co.elastic.apm.agent.sdk.logging.Logger;
import co.elastic.apm.agent.sdk.logging.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;
import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.isInAnyPackage;
import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.isProxy;
import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.overridesOrImplementsMethodThat;
import static co.elastic.apm.agent.impl.transaction.AbstractSpan.PRIO_METHOD_SIGNATURE;
import static co.elastic.apm.agent.impl.transaction.AbstractSpan.PRIO_USER_SUPPLIED;
import static co.elastic.apm.agent.pluginapi.ElasticApmApiInstrumentation.PUBLIC_API_INSTRUMENTATION_GROUP;
import static co.elastic.apm.agent.pluginapi.Utils.FRAMEWORK_NAME;
import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

public class CaptureTransactionInstrumentation extends TracerAwareInstrumentation {

    public static final Logger logger = LoggerFactory.getLogger(CaptureTransactionInstrumentation.class);

    private final CoreConfiguration coreConfig;
    private final StacktraceConfiguration stacktraceConfig;

    public CaptureTransactionInstrumentation(ElasticApmTracer tracer) {
        coreConfig = tracer.getConfig(CoreConfiguration.class);
        stacktraceConfig = tracer.getConfig(StacktraceConfiguration.class);
    }

    public static class AdviceClass {
        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object onMethodEnter(@Advice.Origin Class<?> clazz,
                                           @SimpleMethodSignature String signature,
                                           @AnnotationValueExtractor(annotationClassName = "co.elastic.apm.api.CaptureTransaction", method = "value") String transactionName,
                                           @AnnotationValueExtractor(annotationClassName = "co.elastic.apm.api.CaptureTransaction", method = "type") String type) {
            final Object active = tracer.getActive();
            if (active == null) {
                Transaction transaction = tracer.startRootTransaction(clazz.getClassLoader());
                if (transaction != null) {
                    if (transactionName.isEmpty()) {
                        transaction.withName(signature, PRIO_METHOD_SIGNATURE);
                    } else {
                        transaction.withName(transactionName, PRIO_USER_SUPPLIED);
                    }
                    transaction.withType(type)
                        .activate();
                    transaction.setFrameworkName(FRAMEWORK_NAME);
                    return transaction;
                }
            } else {
                logger.debug("Not creating transaction for method {} because there is already a transaction running ({})", signature, active);
            }
            return null;
        }

        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static void onMethodExit(@Advice.Enter @Nullable Object transaction,
                                        @Advice.Thrown @Nullable Throwable t) {
            if (transaction instanceof Transaction) {
                ((Transaction) transaction)
                    .captureException(t)
                    .withOutcome(t != null ? Outcome.FAILURE : Outcome.SUCCESS)
                    .deactivate()
                    .end();
            }
        }
    }

    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        return classLoaderCanLoadClass("co.elastic.apm.api.CaptureTransaction");
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return isInAnyPackage(stacktraceConfig.getApplicationPackages(), ElementMatchers.<NamedElement>none())
            .and(not(isProxy()))
            .and(declaresMethod(getMethodMatcher()));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        if (coreConfig.isEnablePublicApiAnnotationInheritance()) {
            return overridesOrImplementsMethodThat(isAnnotatedWith(named("co.elastic.apm.api.CaptureTransaction")));
        }
        return isAnnotatedWith(named("co.elastic.apm.api.CaptureTransaction"));
    }

    @Override
    public final Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList(PUBLIC_API_INSTRUMENTATION_GROUP, "annotations");
    }
}
