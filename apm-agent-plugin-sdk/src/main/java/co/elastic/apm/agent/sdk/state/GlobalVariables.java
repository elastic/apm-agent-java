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
package co.elastic.apm.agent.sdk.state;


import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * As the classes of an instrumentation plugins may be loaded from multiple plugin class loaders,
 * there's a need to share global state between those class loaders.
 * <p>
 * An alternative to this is {@link GlobalState} which can be used to make a whole class scoped globally.
 * </p>
 */
public class GlobalVariables {
    private static final ConcurrentMap<String, Object> registry = new ConcurrentHashMap<>();

    /**
     * Gets a global variable given an advice class an additional key which identifies the variable within the class.
     *
     * @param adviceClass  the advice class which uses the global variable.
     * @param key          an additional key which identifies the variable within the class.
     * @param defaultValue the default value of the global variable
     * @param <T>          the type of the variable
     * @return a global variable
     */
    public static <T> T get(Class<?> adviceClass, String key, T defaultValue) {
        key = adviceClass.getName() + "." + key;
        if (defaultValue.getClass().getClassLoader() != null && !defaultValue.getClass().getName().startsWith("co.elastic.apm.agent")) {
            throw new IllegalArgumentException("Registering types specific to an instrumentation plugin would lead to class loader leaks: " + defaultValue);
        }
        T value = (T) registry.get(key);
        if (value == null) {
            registry.putIfAbsent(key, defaultValue);
            value = (T) registry.get(key);
        }
        return value;
    }

}
