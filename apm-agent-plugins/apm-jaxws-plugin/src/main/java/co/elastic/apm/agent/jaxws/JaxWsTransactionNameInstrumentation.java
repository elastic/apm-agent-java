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
package co.elastic.apm.agent.jaxws;

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.bci.bytebuddy.SimpleMethodSignatureOffsetMappingFactory.SimpleMethodSignature;
import co.elastic.apm.agent.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.agent.tracer.Tracer;
import co.elastic.apm.agent.tracer.Transaction;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

import java.util.Collection;
import java.util.Collections;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;
import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.isInAnyPackage;
import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.isProxy;
import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.overridesOrImplementsMethodThat;
import static co.elastic.apm.agent.tracer.AbstractSpan.PRIORITY_HIGH_LEVEL_FRAMEWORK;
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.isBootstrapClassLoader;
import static net.bytebuddy.matcher.ElementMatchers.isDeclaredBy;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.not;

public class JaxWsTransactionNameInstrumentation extends TracerAwareInstrumentation {

    private static final String FRAMEWORK_NAME = "JAX-WS";

    private final Collection<String> applicationPackages;

    public JaxWsTransactionNameInstrumentation(Tracer tracer) {
        applicationPackages = tracer.getConfig(StacktraceConfiguration.class).getApplicationPackages();
    }

    public static class AdviceClass {
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static void setTransactionName(@SimpleMethodSignature String signature) {
            final Transaction<?> transaction = tracer.currentTransaction();
            if (transaction != null) {
                transaction.withName(signature, PRIORITY_HIGH_LEVEL_FRAMEWORK, false);
                transaction.setFrameworkName(FRAMEWORK_NAME);
            }
        }
    }

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        // setting application_packages makes this matcher more performant but is not required
        // could lead to false negative matches when importing a 3rd party library whose JAX-WS resources are exposed
        return isInAnyPackage(applicationPackages, ElementMatchers.<NamedElement>any());
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        // the implementations have to be annotated as well
        // quote from javadoc:
        // "Marks a Java class as implementing a Web Service, or a Java interface as defining a Web Service interface."
        return not(isInterface()).and(not(isProxy())).and(isAnnotatedWith(namedOneOf("javax.jws.WebService", "jakarta.jws.WebService")));
    }

    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        return not(isBootstrapClassLoader())
            .and(classLoaderCanLoadClass("javax.jws.WebService")
                .or(classLoaderCanLoadClass("jakarta.jws.WebService")));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return overridesOrImplementsMethodThat(
            isPublic().and(isDeclaredBy(isInterface()))
        ).whereHierarchyContains(
            isInterface().and(isAnnotatedWith(namedOneOf("javax.jws.WebService", "jakarta.jws.WebService")))
        ).onSuperClassesThat(
            isInAnyPackage(applicationPackages, ElementMatchers.<NamedElement>any())
        );
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singletonList("jax-ws");
    }

}
