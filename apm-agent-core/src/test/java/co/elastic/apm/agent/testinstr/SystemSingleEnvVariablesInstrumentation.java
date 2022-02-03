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
package co.elastic.apm.agent.testinstr;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import javax.annotation.Nullable;

import static net.bytebuddy.implementation.bytecode.assign.Assigner.Typing.DYNAMIC;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class SystemSingleEnvVariablesInstrumentation extends SystemEnvVariableInstrumentation {

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return isStatic().and(named("getenv").and(takesArguments(1)));
    }

    public static class AdviceClass {
        // note: requires to use the array return form in order to be able to set return value to 'null'
        @Advice.AssignReturned.ToReturned(index = 0, typing = DYNAMIC)
        @Advice.OnMethodExit(onThrowable = Throwable.class, inline = false)
        public static Object[] alterEnvVariables(@Advice.Argument(0) String varName, @Advice.Return @Nullable String ret) {
            return new Object[]{getCustomEnvironmentEntry(varName, ret)};
        }
    }
}
