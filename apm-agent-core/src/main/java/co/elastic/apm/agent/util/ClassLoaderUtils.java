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
package co.elastic.apm.agent.util;

import javax.annotation.Nullable;

public class ClassLoaderUtils {

    /**
     * Checks whether the provided {@link ClassLoader} may be unloaded like a web application class loader, for example.
     * <p>
     * If the class loader can't be unloaded, it is safe to use {@link ThreadLocal}s and to reuse the {@code WeakConcurrentMap.LookupKey}.
     * Otherwise, the use of {@link ThreadLocal}s may lead to class loader leaks as it prevents the class loader this class
     * is loaded by to unload.
     * </p>
     *
     * @param classLoader The class loader to check.
     * @return {@code true} if the provided class loader can be unloaded.
     */
    public static boolean isPersistentClassLoader(@Nullable ClassLoader classLoader) {
        try {
            return classLoader == null // bootstrap class loader
                || classLoader == ClassLoader.getSystemClassLoader()
                || classLoader == ClassLoader.getSystemClassLoader().getParent(); // ext/platfrom class loader;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isAgentClassLoader(@Nullable ClassLoader classLoader) {
        return classLoader != null && classLoader.getClass().getName().startsWith("co.elastic.apm");
    }

    public static boolean isBootstrapClassLoader(@Nullable ClassLoader classLoader) {
        return classLoader == null;
    }
}
