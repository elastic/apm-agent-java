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
package co.elastic.apm.agent.jaxrs;

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.impl.stacktrace.StacktraceConfiguration;
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
import java.util.Collections;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;
import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.isInAnyPackage;
import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.isProxy;
import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.overridesOrImplementsMethodThat;
import static co.elastic.apm.agent.tracer.AbstractSpan.PRIORITY_HIGH_LEVEL_FRAMEWORK;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.isBootstrapClassLoader;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.not;

public abstract class JaxRsTransactionNameInstrumentation extends TracerAwareInstrumentation {

    private final Collection<String> applicationPackages;
    private final JaxRsConfiguration configuration;
    private final Tracer tracer;

    public JaxRsTransactionNameInstrumentation(Tracer tracer) {
        this.tracer = tracer;
        applicationPackages = tracer.getConfig(StacktraceConfiguration.class).getApplicationPackages();
        configuration = tracer.getConfig(JaxRsConfiguration.class);
    }

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        // setting application_packages makes this matcher more performant but is not required
        // could lead to false negative matches when importing a 3rd party library whose JAX-RS resources are exposed
        return isInAnyPackage(applicationPackages, ElementMatchers.<NamedElement>any());
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        // quote from JAX-RS 2.0 spec (section 3.6 Annotation Inheritance)
        // "Note that inheritance of class or interface annotations is not supported."
        // However, at least Jersey also supports the @Path to be at a parent class/interface.
        // If annotation inheritance is not needed, the user may turn it of for better startup performance
        // (matching on the class hierarchy vs matching one class)
        if (configuration.isEnableJaxrsAnnotationInheritance()) {
            return not(isInterface())
                .and(not(isProxy()))
                .and(isAnnotatedWith(named(pathClassName()))
                    .or(hasSuperType(isAnnotatedWith(namedOneOf(pathClassName()))))
                );
        } else {
            return isAnnotatedWith(namedOneOf(pathClassName()));
        }
    }

    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        return not(isBootstrapClassLoader())
            .and(classLoaderCanLoadClass(pathClassName()));
    }

    abstract String pathClassName();

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        // quote from JAX-RS 2.0 spec (section 3.6 Annotation Inheritance)
        // "JAX-RS annotations may be used on the methods and method parameters of a super-class or an implemented interface."
        return overridesOrImplementsMethodThat(
            isAnnotatedWith(
                namedOneOf("javax.ws.rs.GET", "jakarta.ws.rs.GET")
                    .or(namedOneOf("javax.ws.rs.POST", "jakarta.ws.rs.POST"))
                    .or(namedOneOf("javax.ws.rs.PUT", "jakarta.ws.rs.PUT"))
                    .or(namedOneOf("javax.ws.rs.DELETE", "jakarta.ws.rs.DELETE"))
                    .or(namedOneOf("javax.ws.rs.PATCH", "jakarta.ws.rs.PATCH"))
                    .or(namedOneOf("javax.ws.rs.HEAD", "jakarta.ws.rs.HEAD"))
                    .or(namedOneOf("javax.ws.rs.OPTIONS", "jakarta.ws.rs.OPTIONS"))))
            .onSuperClassesThat(isInAnyPackage(applicationPackages, ElementMatchers.<NamedElement>any()));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singletonList("jax-rs");
    }

    @Nullable
    @Override
    public Advice.OffsetMapping.Factory<?> getOffsetMapping() {
        return new JaxRsOffsetMappingFactory(tracer);
    }

    static class BaseAdvice {
        private static final boolean useAnnotationValueForTransactionName = GlobalTracer.get()
            .getConfig(JaxRsConfiguration.class)
            .isUseJaxRsPathForTransactionName();

        protected static void setTransactionName(String signature, @Nullable String pathAnnotationValue, @Nullable String frameworkVersion) {
            final Transaction<?> transaction = TracerAwareInstrumentation.tracer.currentTransaction();
            if (transaction != null) {
                String transactionName = signature;
                if (useAnnotationValueForTransactionName) {
                    if (pathAnnotationValue != null) {
                        transactionName = pathAnnotationValue;
                    }
                }
                transaction.withName(transactionName, PRIORITY_HIGH_LEVEL_FRAMEWORK, false);
                transaction.setFrameworkName("JAX-RS");
                transaction.setFrameworkVersion(frameworkVersion);
            }
        }
    }
}
