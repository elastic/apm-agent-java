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
package co.elastic.apm.agent.logging;

import co.elastic.apm.agent.sdk.state.GlobalState;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakConcurrent;
import co.elastic.apm.agent.sdk.weakconcurrent.WeakSet;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Allows the agent to put/remove into all registered JulMdc classes.
 * Must be loaded exactly once in agent classloader to be accessible to all plugins.
 */
@GlobalState
public class JulMdcAccessor {

    private static final WeakSet<Class<?>> registeredMdcClasses = WeakConcurrent.buildSet();

    private static final List<MethodHandle> putHandles = new ArrayList<MethodHandle>();
    private static final List<MethodHandle> removeHandles = new ArrayList<MethodHandle>();

    private JulMdcAccessor() {
    }

    public static void register(Class<?> mdcClass) {
        if (!registeredMdcClasses.add(mdcClass)) {
            return;
        }

        MethodHandle putHandle;
        MethodHandle removeHandle;
        try {
            putHandle = MethodHandles.lookup().findStatic(mdcClass, "put", MethodType.methodType(void.class, String.class, String.class));
            removeHandle = MethodHandles.lookup().findStatic(mdcClass, "remove", MethodType.methodType(void.class, String.class));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // copy all the entries from other registered instances, in case registration happen after values have been
        // added to other MDCs.
        for (Class<?> otherMdcClass : registeredMdcClasses) {
            if (otherMdcClass != mdcClass) {
                Map<String, String> otherValues = getEntries(otherMdcClass);
                for (Map.Entry<String, String> entry : otherValues.entrySet()) {
                    try {
                        putHandle.invoke(entry.getKey(), entry.getValue());
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }

        putHandles.add(putHandle);
        removeHandles.add(removeHandle);

    }

    public static void putAll(String key, String value) {
        for (int i = 0; i < putHandles.size(); i++) {
            try {
                putHandles.get(i).invoke(key, value);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void removeAll(String key) {
        for (int i = 0; i < removeHandles.size(); i++) {
            try {
                removeHandles.get(i).invoke(key);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Public access only for testing
     *
     * @return entries in a given mdc class
     */
    public static Map<String, String> getEntries(Class<?> mdcClass) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, String> entries = (Map<String, String>) MethodHandles.lookup()
                .findStatic(mdcClass, "getEntries", MethodType.methodType(Map.class)).invoke();
            return entries;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
