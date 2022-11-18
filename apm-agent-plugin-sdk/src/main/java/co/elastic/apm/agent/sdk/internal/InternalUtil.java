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
package co.elastic.apm.agent.sdk.internal;

import java.security.AccessController;
import java.security.PrivilegedAction;

public class InternalUtil {

    /**
     * Returns the classloader of a given class, optionally wrapping in a privileged action if security manager is enabled
     *
     * @param type           class whose classloader needs to be retrieved
     * @param systemFallback {@literal true} if fallback to system CL is required, {@literal false} otherwise
     * @return class classloader or system classloader if it was loaded in system classloader
     */
    public static ClassLoader getClassLoader(final Class<?> type, final boolean systemFallback) {
        if (System.getSecurityManager() == null) {
            return doGetClassLoader(type, systemFallback);
        }

        return AccessController.doPrivileged(new PrivilegedAction<ClassLoader>() {
            @Override
            public ClassLoader run() {
                return doGetClassLoader(type, systemFallback);
            }
        });
    }

    private static ClassLoader doGetClassLoader(Class<?> type, boolean systemFallback) {
        ClassLoader classLoader = type.getClassLoader();
        if (classLoader == null) {
            classLoader = ClassLoader.getSystemClassLoader();
        }
        return classLoader;
    }
}
