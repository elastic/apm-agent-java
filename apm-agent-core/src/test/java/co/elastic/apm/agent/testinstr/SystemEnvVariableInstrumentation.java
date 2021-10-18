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

import co.elastic.apm.agent.bci.TracerAwareInstrumentation;
import co.elastic.apm.agent.sdk.state.GlobalVariables;
import co.elastic.apm.agent.sdk.weakconcurrent.DetachedThreadLocal;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakConcurrent;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static net.bytebuddy.matcher.ElementMatchers.named;

public abstract class SystemEnvVariableInstrumentation extends TracerAwareInstrumentation {

    protected static final DetachedThreadLocal<Map<String, String>> customEnvVariablesTL =
        GlobalVariables.get(SystemEnvVariableInstrumentation.class, "customEnvVariables", WeakConcurrent.<Map<String, String>>buildThreadLocal());

    /**
     * Sets custom env variables that will be added to the actual env variables returned by {@link System#getenv()} or
     * {@link System#getenv(String)} on the current thread.
     * NOTE: caller must clear the custom variables when they are not required anymore through {@link #clearCustomEnvVariables()}.
     * @param customEnvVariables a map of key-value pairs that will be appended to the actual environment variables
     *                           returned by {@link System#getenv()} on the current thread
     */
    public static void setCustomEnvVariables(Map<String, String> customEnvVariables) {
        customEnvVariablesTL.set(customEnvVariables);
    }

    public static void clearCustomEnvVariables() {
        customEnvVariablesTL.remove();
    }

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return named("java.lang.System");
    }

    @Override
    public Collection<String> getInstrumentationGroupNames() {
        return Collections.emptyList();
    }

    public static class AdviceClass {
        @Advice.AssignReturned.ToReturned
        @Advice.OnMethodExit(onThrowable = Throwable.class, inline = false)
        public static Map<String, String> appendToEnvVariables(@Advice.Return Map<String, String> ret) {
            Map<String, String> customEnvVariables = customEnvVariablesTL.get();
            if (customEnvVariables != null && !customEnvVariables.isEmpty()) {
                ret = new HashMap<>(ret);
                ret.putAll(customEnvVariables);
            }
            return ret;
        }
    }
}
