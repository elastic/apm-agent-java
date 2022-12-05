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

import javax.annotation.Nullable;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ServiceLoader;

public class InternalUtil {

    @Nullable
    public static ClassLoader getClassLoader(final Class<?> type) {
        if (System.getSecurityManager() == null) {
            return type.getClassLoader();
        }
        return AccessController.doPrivileged(new PrivilegedAction<ClassLoader>() {
            @Nullable
            @Override
            public ClassLoader run() {
                return type.getClassLoader();
            }
        });
    }

    /**
     * Loads a service provider based on the service interface, assuming that a provider (implementation) is available to the class
     * loader that loads the service (interface). This allows to separate interface and implementation without introducing a
     * dependency on the implementation class or class name.
     * More specifically, this utility provides a way to declare an interface in the SDK and an implementation in the agent core module
     *
     * @param serviceInterface the service interface class
     * @param <T> service type
     * @return a service provider that is loaded by the class loader that loads the service interface
     */
    public static <T> T getServiceProvider(final Class<T> serviceInterface) {
        if (System.getSecurityManager() == null) {
            return internalGetServiceProvider(serviceInterface);
        }
        return AccessController.doPrivileged(new PrivilegedAction<T>() {
            @Nullable
            @Override
            public T run() {
                return internalGetServiceProvider(serviceInterface);
            }
        });
    }

    private static <T> T internalGetServiceProvider(final Class<T> serviceInterface) {
        ClassLoader classLoader = serviceInterface.getClassLoader();
        if (classLoader == null) {
            classLoader = ClassLoader.getSystemClassLoader();
        }
        return ServiceLoader.load(serviceInterface, classLoader).iterator().next();
    }
}
