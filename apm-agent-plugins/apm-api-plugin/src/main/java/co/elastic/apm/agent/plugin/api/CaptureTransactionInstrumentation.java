/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
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
package co.elastic.apm.agent.plugin.api;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.bci.VisibleForAdvice;
import co.elastic.apm.agent.bci.bytebuddy.AnnotationValueOffsetMappingFactory.AnnotationValueExtractor;
import co.elastic.apm.agent.bci.bytebuddy.SimpleMethodSignatureOffsetMappingFactory.SimpleMethodSignature;
import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.agent.impl.transaction.Transaction;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;
import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.isInAnyPackage;
import static co.elastic.apm.agent.impl.transaction.AbstractSpan.PRIO_METHOD_SIGNATURE;
import static co.elastic.apm.agent.impl.transaction.AbstractSpan.PRIO_USER_SUPPLIED;
import static co.elastic.apm.agent.plugin.api.ElasticApmApiInstrumentation.PUBLIC_API_INSTRUMENTATION_GROUP;
import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.named;

public class CaptureTransactionInstrumentation extends ElasticApmInstrumentation {

    @VisibleForAdvice
    public static final Logger logger = LoggerFactory.getLogger(CaptureTransactionInstrumentation.class);

    private final StacktraceConfiguration config;

    public CaptureTransactionInstrumentation(ElasticApmTracer tracer) {
        config = tracer.getConfig(StacktraceConfiguration.class);
    }

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onMethodEnter(@Advice.Origin Class<?> clazz,
                                     @SimpleMethodSignature String signature,
                                     @AnnotationValueExtractor(annotationClassName = "co.elastic.apm.api.CaptureTransaction", method = "value") String transactionName,
                                     @AnnotationValueExtractor(annotationClassName = "co.elastic.apm.api.CaptureTransaction", method = "type") String type,
                                     @Advice.Local("transaction") Transaction transaction) {
        if (tracer != null) {
            final Object active = tracer.getActive();
            if (active == null) {
                transaction = tracer.startRootTransaction(clazz.getClassLoader());
                if (transaction != null) {
                    if (transactionName.isEmpty()) {
                        transaction.withName(signature, PRIO_METHOD_SIGNATURE);
                    } else {
                        transaction.withName(transactionName, PRIO_USER_SUPPLIED);
                    }
                    transaction.withType(type)
                        .activate();
                }
            } else {
                logger.debug("Not creating transaction for method {} because there is already a transaction running ({})", signature, active);
            }
        }
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onMethodExit(@Nullable @Advice.Local("transaction") Transaction transaction,
                                    @Advice.Thrown Throwable t) {
        if (transaction != null) {
            transaction.captureException(t)
                .deactivate()
                .end();
        }
    }

    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        return classLoaderCanLoadClass("co.elastic.apm.api.CaptureTransaction");
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return isInAnyPackage(config.getApplicationPackages(), ElementMatchers.<NamedElement>none())
            .and(declaresMethod(getMethodMatcher()));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return isAnnotatedWith(named("co.elastic.apm.api.CaptureTransaction"));
    }

    @Override
    public final Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList(PUBLIC_API_INSTRUMENTATION_GROUP, "annotations");
    }

}
