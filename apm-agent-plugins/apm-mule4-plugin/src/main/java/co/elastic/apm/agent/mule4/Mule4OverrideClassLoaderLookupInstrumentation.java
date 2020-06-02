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
package co.elastic.apm.agent.mule4;

import co.elastic.apm.agent.bci.ElasticApmInstrumentation;
import co.elastic.apm.agent.bci.bytebuddy.postprocessor.AssignToReturn;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.mule.runtime.module.artifact.api.classloader.DelegateOnlyLookupStrategy;
import org.mule.runtime.module.artifact.api.classloader.LookupStrategy;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

public class Mule4OverrideClassLoaderLookupInstrumentation extends ElasticApmInstrumentation {

    @Override
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return nameContains("ClassLoaderLookupPolicy");
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return not(isInterface()).and(hasSuperType(named("org.mule.runtime.module.artifact.api.classloader.ClassLoaderLookupPolicy")));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return named("getPackageLookupStrategy").and(takesArgument(0, String.class));
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.singletonList("mule");
    }

    @Override
    public boolean includeWhenInstrumentationIsDisabled() {
        return true;
    }

    @Override
    public Class<?> getAdviceClass() {
        return Mule4OverrideClassLoaderLookupAdvice.class;
    }

    public static class Mule4OverrideClassLoaderLookupAdvice {
        @AssignToReturn
        @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
        public static LookupStrategy makeParentOnlyForAgentClasses(@Advice.Argument(0) @Nullable final String packageName,
                                                                   @Advice.Return LookupStrategy lookupStrategy) {

            // Until instrumentation mechanism is initiated, agent classes get loaded from the launching class loader.
            // Whenever flows are invoked with the visibility of this class loader's classpath, we can't let other agent
            // classes to be loaded from System or Bootstrap class loader.
            // Mule4OverrideClassLoaderLookupInstrumentation is a good indication of that, as it is guaranteed to be
            // loaded before this instrumentation took place
            if (packageName != null && packageName.startsWith("co.elastic.apm.agent") &&
                Mule4OverrideClassLoaderLookupInstrumentation.class.getClassLoader() == null) {
                lookupStrategy = new DelegateOnlyLookupStrategy(ClassLoader.getSystemClassLoader());
            }
            return lookupStrategy;
        }
    }
}
