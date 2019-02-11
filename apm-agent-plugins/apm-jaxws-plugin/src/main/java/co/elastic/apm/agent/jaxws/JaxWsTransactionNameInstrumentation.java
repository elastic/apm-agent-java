/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.agent.jaxws;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;
import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.isInAnyPackage;
import static co.elastic.apm.agent.bci.bytebuddy.CustomElementMatchers.overridesOrImplementsMethodThat;
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.isBootstrapClassLoader;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

public class JaxWsTransactionNameInstrumentation extends ElasticApmInstrumentation {

    private Collection<String> applicationPackages = Collections.emptyList();

    @Advice.OnMethodEnter(suppress = Throwable.class)
    private static void setTransactionName(@SimpleMethodSignature String signature) {
        if (tracer != null) {
            final Transaction transaction = tracer.currentTransaction();
            if (transaction != null && transaction.getName().length() == 0) {
                transaction.withName(signature);
            }
        }
    }

    @Override
    public void init(ElasticApmTracer tracer) {
        applicationPackages = tracer.getConfig(StacktraceConfiguration.class).getApplicationPackages();
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
        return isAnnotatedWith(named("javax.jws.WebService")).and(not(isInterface()));
    }

    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        return not(isBootstrapClassLoader())
            .and(classLoaderCanLoadClass("javax.jws.WebService"));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return overridesOrImplementsMethodThat(
            isAnnotatedWith(
                named("javax.jws.WebMethod")))
            .onSuperClassesThat(isInAnyPackage(applicationPackages, ElementMatchers.<NamedElement>any()));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("jax-ws", "jax-ws-annotations");
    }
}
