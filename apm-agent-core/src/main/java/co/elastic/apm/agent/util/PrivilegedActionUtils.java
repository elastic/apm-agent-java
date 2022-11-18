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
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Map;

/**
 * Delegates calls to {@link System} with wrapping in privileged actions which is required when security manager is active
 */
public class PrivilegedActionUtils {

    @Nullable
    public static String getEnv(final String key) {
        if (System.getSecurityManager() == null) {
            return System.getenv(key);
        }

        return AccessController.doPrivileged(new PrivilegedAction<String>() {
            @Override
            public String run() {
                return System.getenv(key);
            }
        });
    }

    public static Map<String, String> getEnv() {
        if (System.getSecurityManager() == null) {
            return System.getenv();
        }

        return AccessController.doPrivileged(new PrivilegedAction<Map<String, String>>() {
            @Override
            public Map<String, String> run() {
                return System.getenv();
            }
        });
    }

    @Nullable
    public static ClassLoader getClassLoader(final Class<?> type) {
        if (System.getSecurityManager() == null) {
            return type.getClassLoader();
        }

        return AccessController.doPrivileged(new PrivilegedAction<ClassLoader>() {
            @Override
            public ClassLoader run() {
                return type.getClassLoader();
            }
        });
    }

    public static ClassLoader getContextClassLoader(final Thread t){
        if (System.getSecurityManager() == null) {
            return t.getContextClassLoader();
        }

        return AccessController.doPrivileged(new PrivilegedAction<ClassLoader>() {
            @Override
            public ClassLoader run() {
                return t.getContextClassLoader();
            }
        });
    }

    public static void setContextClassLoader(final Thread t, final @Nullable ClassLoader cl){
        if(System.getSecurityManager() == null){
            t.setContextClassLoader(cl);
        }

        AccessController.doPrivileged(new PrivilegedAction<Object>() {
            @Nullable
            @Override
            public Object run() {
                t.setContextClassLoader(cl);
                return null;
            }
        });

    }

    public static Thread newThread(final @Nullable Runnable r){
        if(System.getSecurityManager() == null){
            return new Thread(r);
        }

        return AccessController.doPrivileged(new PrivilegedAction<Thread>() {
            @Override
            public Thread run() {
                return new Thread(r);
            }
        });
    }

}
