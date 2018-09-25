/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 Elastic and contributors
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
package co.elastic.apm.jaxrs;

import co.elastic.apm.bci.ElasticApmInstrumentation;
import co.elastic.apm.bci.bytebuddy.SimpleMethodSignatureOffsetMappingFactory.SimpleMethodSignature;
import co.elastic.apm.impl.ElasticApmTracer;
import co.elastic.apm.impl.stacktrace.StacktraceConfiguration;
import co.elastic.apm.impl.transaction.Transaction;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static co.elastic.apm.bci.bytebuddy.CustomElementMatchers.classLoaderCanLoadClass;
import static co.elastic.apm.bci.bytebuddy.CustomElementMatchers.isInAnyPackage;
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.isBootstrapClassLoader;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

public class JaxRsTransactionNameInstrumentation extends ElasticApmInstrumentation {

    private Collection<String> applicationPackages = Collections.emptyList();

    @Advice.OnMethodEnter
    private static void setTransactionName(@SimpleMethodSignature String signature) {
        if (tracer != null) {
            final Transaction transaction = tracer.currentTransaction();
            if (transaction != null) {
                transaction.withName(signature);
            }
        }
    }

    @Override
    public void init(ElasticApmTracer tracer) {
        applicationPackages = tracer.getConfig(StacktraceConfiguration.class).getApplicationPackages();
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        // setting application_packages makes this matcher more performant but is not required
        return isInAnyPackage(applicationPackages, ElementMatchers.<TypeDescription>any())
            .and(isAnnotatedWith(named("javax.ws.rs.Path")));
    }

    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        return not(isBootstrapClassLoader())
            .and(classLoaderCanLoadClass("javax.ws.rs.Path"));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return isAnnotatedWith(named("javax.ws.rs.GET"))
            .or(isAnnotatedWith(named("javax.ws.rs.POST")))
            .or(isAnnotatedWith(named("javax.ws.rs.PUT")))
            .or(isAnnotatedWith(named("javax.ws.rs.DELETE")))
            .or(isAnnotatedWith(named("javax.ws.rs.HEAD")))
            .or(isAnnotatedWith(named("javax.ws.rs.OPTIONS")));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Arrays.asList("jax-rs", "jax-rs-annotations");
    }
}
