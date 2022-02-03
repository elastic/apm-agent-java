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

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static net.bytebuddy.matcher.ElementMatchers.named;

public abstract class SystemEnvVariableInstrumentation extends TracerAwareInstrumentation {

    private static final DetachedThreadLocal<Map<String, String>> customEnvVariablesTL =
        GlobalVariables.get(SystemEnvVariableInstrumentation.class, "customEnvVariables", WeakConcurrent.<Map<String, String>>buildThreadLocal());

    private static final String NULL_ENTRY = "null";

    /**
     * Sets custom env variables that will be added to the actual env variables returned by {@link System#getenv()} or
     * {@link System#getenv(String)} on the current thread. Entries with {@literal null} values will allow to emulate
     * when those environment variables are not set.
     * <p>
     * NOTE: caller must clear the custom variables when they are not required anymore through {@link #clearCustomEnvVariables()}.
     *
     * @param customEnvVariables a map of key-value pairs that will be appended to the actual environment variables
     *                           returned by {@link System#getenv()} on the current thread
     */
    public static void setCustomEnvVariables(Map<String, String> customEnvVariables) {
        Map<String,String> map = new HashMap<>();
        for (Map.Entry<String, String> entry : customEnvVariables.entrySet()) {
            String value = entry.getValue();
            if (value == null) {
                value = NULL_ENTRY;
            }
            map.put(entry.getKey(), value);
        }
        customEnvVariablesTL.set(map);
    }

    protected static Map<String,String> getCustomEnvironmentMap(Map<String,String> originalValues){
        Map<String,String> customMap = customEnvVariablesTL.get();
        if(customMap == null){
            return originalValues;
        }
        Map<String,String> map = new HashMap<>(originalValues);
        for (Map.Entry<String, String> entry: customMap.entrySet()) {
            String value = entry.getValue();

            //noinspection StringEquality
            if(value == null || value == NULL_ENTRY){
                map.remove(entry.getKey());
            } else {
                map.put(entry.getKey(), entry.getValue());
            }
        }

        return map;
    }

    protected static String getCustomEnvironmentEntry(String key, @Nullable String originalValue) {
        Map<String,String> customMap = customEnvVariablesTL.get();
        if (customMap == null) {
            return originalValue;
        }
        String customValue = customMap.get(key);

        //noinspection StringEquality
        if(customValue == NULL_ENTRY){
            return null;
        } else if (customValue != null){
            return customValue;
        }
        return originalValue;
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
        public static Map<String, String> alterEnvVariables(@Advice.Return Map<String, String> ret) {
            return getCustomEnvironmentMap(ret);
        }
    }
}
