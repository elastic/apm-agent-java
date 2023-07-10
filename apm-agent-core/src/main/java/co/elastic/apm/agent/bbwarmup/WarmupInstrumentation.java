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
package co.elastic.apm.agent.bbwarmup;

import co.elastic.apm.agent.sdk.bytebuddy.SimpleMethodSignatureOffsetMappingFactory;
import co.elastic.apm.agent.sdk.ElasticApmInstrumentation;
import co.elastic.apm.agent.sdk.internal.util.PrivilegedActionUtils;
import co.elastic.apm.agent.tracer.GlobalTracer;
import co.elastic.apm.agent.tracer.Tracer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;

import static co.elastic.apm.agent.sdk.bytebuddy.CustomElementMatchers.isSameClassLoader;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesNoArguments;

public class WarmupInstrumentation extends ElasticApmInstrumentation {

    static {
        // assure initialization of tracer
        @SuppressWarnings("unused")
        Tracer tracer = GlobalTracer.get();
    }

    @Override
    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        // Instrumenting the transformed class loaded by the net.bytebuddy.dynamic.loading.ByteArrayClassLoader.ChildFirst
        // class loader in co.elastic.apm.agent.bci.bytebuddy.InstallationListenerImpl may produce java.lang.instrument.UnmodifiableClassException
        // (caused by java.lang.ClassFormatError) on OpenJDK 7.
        // By allowing instrumentation only when the test class is loaded by the same class loader that loads this
        // instrumentation class, we avoid this problem and still allow it to work both on production and unit tests
        return isSameClassLoader(PrivilegedActionUtils.getClassLoader(getClass()));
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("co.elastic.apm.agent.bci.bytebuddy.Instrumented");
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return not(isStatic()).and(named("isInstrumented")).and(takesNoArguments()).and(returns(boolean.class));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.emptyList();
    }

    @Override
    public boolean includeWhenInstrumentationIsDisabled() {
        return true;
    }

    public static class AdviceClass {
        @Nullable
        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static Object onMethodEnter(
            @Advice.This Object thiz,
            @SimpleMethodSignatureOffsetMappingFactory.SimpleMethodSignature String signature) {
            return null;
        }

        @Advice.AssignReturned.ToReturned
        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static boolean onMethodExit(@Advice.Enter @Nullable Object nullFromEnter,
                                           @Advice.Thrown @Nullable Throwable t) {
            return true;
        }
    }
}
