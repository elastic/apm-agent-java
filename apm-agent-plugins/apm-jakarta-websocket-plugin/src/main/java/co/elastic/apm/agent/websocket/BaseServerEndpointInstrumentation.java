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
package co.elastic.apm.agent.websocket;

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.agent.tracer.Outcome;
import co.elastic.apm.agent.tracer.Tracer;
import co.elastic.apm.agent.tracer.Transaction;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

import javax.annotation.Nullable;
import java.util.Collection;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;
import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.isInAnyPackage;
import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.isProxy;
import static co.elastic.apm.agent.tracer.AbstractSpan.PRIORITY_HIGH_LEVEL_FRAMEWORK;
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.isBootstrapClassLoader;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.not;

public abstract class BaseServerEndpointInstrumentation extends TracerAwareInstrumentation {

    private final Collection<String> applicationPackages;

    public BaseServerEndpointInstrumentation(Tracer tracer) {
        applicationPackages = tracer.getConfig(StacktraceConfiguration.class).getApplicationPackages();
    }

    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        return not(isBootstrapClassLoader())
            .and(classLoaderCanLoadClass(getServerEndpointClassName()));
    }

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return isInAnyPackage(applicationPackages, ElementMatchers.<NamedElement>any());
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return not(isInterface()).and(not(isProxy())).and(isAnnotatedWith(named(getServerEndpointClassName())));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return isAnnotatedWith(
            namedOneOf("javax.websocket.OnOpen", "jakarta.websocket.OnOpen")
                .or(namedOneOf("javax.websocket.OnMessage", "jakarta.websocket.OnMessage"))
                .or(namedOneOf("javax.websocket.OnError", "jakarta.websocket.OnError"))
                .or(namedOneOf("javax.websocket.OnClose", "jakarta.websocket.OnClose")));
    }

    protected abstract String getServerEndpointClassName();

    protected static class BaseAdvice {

        @Nullable
        protected static Object startTransactionOrSetTransactionName(String signature, String frameworkName, @Nullable String frameworkVersion) {
            Transaction<?> currentTransaction = tracer.currentTransaction();
            if (currentTransaction == null) {
                Transaction<?> rootTransaction = tracer.startRootTransaction(Thread.currentThread().getContextClassLoader());
                if (rootTransaction != null) {
                    setTransactionTypeAndName(rootTransaction, signature, frameworkName, frameworkVersion);
                    return rootTransaction.activate();
                }
            } else {
                setTransactionTypeAndName(currentTransaction, signature, frameworkName, frameworkVersion);
            }

            return null;
        }

        protected static void endTransaction(@Nullable Object transactionOrNull, @Advice.Thrown @Nullable Throwable t) {
            if (transactionOrNull == null) {
                return;
            }

            Transaction<?> transaction = (Transaction<?>) transactionOrNull;
            try {
                if (t != null) {
                    transaction.captureException(t).withOutcome(Outcome.FAILURE);
                }
            } finally {
                transaction.deactivate().end();
            }
        }

        private static void setTransactionTypeAndName(Transaction<?> transaction, String signature, String frameworkName, @Nullable String frameworkVersion) {
            transaction.withType(Transaction.TYPE_REQUEST);
            transaction.withName(signature, PRIORITY_HIGH_LEVEL_FRAMEWORK, false);
            transaction.setFrameworkName(frameworkName);
            transaction.setFrameworkVersion(frameworkVersion);
        }
    }
}
