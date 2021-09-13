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
package co.elastic.apm.agent.bootdelegation;

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.is;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

/**
 * Enables universal delegation to the bootstrap class loader for agent classes.
 * <p>
 * Some frameworks like OSGi, JBoss modules or Mule have class loader that restrict the regular delegation to the bootstrap class loader
 * to only a particular set of allowed packages.
 * This is a generic solution that works for all of them.
 * </p>
 * <p>
 * After all plugins have been migrated to indy plugins,
 * this instrumentation can be removed.
 * But having it in now allows to make runtime attachment more readily available sooner.
 * Also, if indy plugins should not work out for some reason,
 * we have already tested out this approach and thus have something to fall back to.
 * </p>
 * <p>
 * This approach is inspired by {@code io.opentelemetry.auto.instrumentation.javaclassloader.ClassLoaderInstrumentation},
 * under Apache License 2.0
 * </p>
 */
public class BootstrapDelegationClassLoaderInstrumentation extends TracerAwareInstrumentation {

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return ElementMatchers.nameContains("Loader");
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return not(nameStartsWith("java."))
            .and(not(nameStartsWith("jdk.")))
            .and(not(nameStartsWith("com.sun.")))
            .and(not(nameStartsWith("sun.")))
            .and(not(nameContains("Bootstrap")))
            .and(hasSuperType(is(ClassLoader.class)));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("loadClass")
            .and(returns(Class.class))
            .and(
                takesArguments(String.class)
                    .or(takesArguments(String.class, boolean.class)));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singletonList("bootdelegation");
    }

    @Override
    public String getAdviceClassName() {
        return getClass().getName() + "$BootstrapAdvice";
    }

    public static class BootstrapAdvice {

        @Advice.OnMethodExit(onThrowable = ClassNotFoundException.class)
        private static void onExit(@Advice.Thrown(readOnly = false) @Nullable ClassNotFoundException thrown,
                                   @Advice.Argument(0) String className,
                                   @Advice.Return(readOnly = false) Class<?> returnValue) {
            // only if the class loader wasn't able to load the agent classes themselves we apply our magic
            if (thrown != null && className.startsWith("co.elastic.apm.agent")) {
                try {
                    returnValue = Class.forName(className, false, null);
                    thrown = null;
                } catch (ClassNotFoundException e) {
                    thrown.addSuppressed(e);
                }
            }
        }

    }

    @Override
    public boolean indyPlugin() {
        return false;
    }
}
