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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ProxySelector;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.security.ProtectionDomain;
import java.util.Map;

/**
 * Delegates calls with wrapping in privileged actions which is required when security manager is active
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
    public static String getProperty(final String name) {
        if (System.getSecurityManager() == null) {
            return System.getProperty(name);
        }

        return AccessController.doPrivileged(new PrivilegedAction<String>() {
            @Override
            public String run() {
                return System.getProperty(name);
            }
        });
    }

    @Nullable
    public static ProxySelector getDefaultProxySelector() {
        if (System.getSecurityManager() == null) {
            return ProxySelector.getDefault();
        }

        return AccessController.doPrivileged(new PrivilegedAction<ProxySelector>() {
            @Override
            public ProxySelector run() {
                return ProxySelector.getDefault();
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

    public static ProtectionDomain getProtectionDomain(final Class<?> type) {
        if (System.getSecurityManager() == null) {
            return type.getProtectionDomain();
        }
        return AccessController.doPrivileged(new PrivilegedAction<ProtectionDomain>() {
            @Override
            public ProtectionDomain run() {
                return type.getProtectionDomain();
            }
        });
    }

    @Nullable
    public static ClassLoader getContextClassLoader(final Thread t) {
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

    public static void setContextClassLoader(final Thread t, final @Nullable ClassLoader cl) {
        if (System.getSecurityManager() == null) {
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

    public static Thread newThread(final @Nullable Runnable r) {
        if (System.getSecurityManager() == null) {
            return new Thread(r);
        }
        return AccessController.doPrivileged(new PrivilegedAction<Thread>() {
            @Override
            public Thread run() {
                return new Thread(r);
            }
        });
    }

    public static FileInputStream newFileInputStream(final File file) throws FileNotFoundException {
        if (System.getSecurityManager() == null) {
            return new FileInputStream(file);
        }
        try {
            return AccessController.doPrivileged(new PrivilegedExceptionAction<FileInputStream>() {
                @Override
                public FileInputStream run() throws Exception {
                    return new FileInputStream(file);
                }
            });
        } catch (PrivilegedActionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof FileNotFoundException) {
                throw (FileNotFoundException) cause;
            }
            throw new RuntimeException(cause);
        }
    }

    /**
     * Creates directory and its parents when path does not exist
     *
     * @param path directory path to create
     * @throws IOException when there is an IO error
     */
    public static void createDirectories(final Path path) throws IOException {
        if (System.getSecurityManager() == null) {
            doCreateDirectories(path);
        }
        try {
            AccessController.doPrivileged(new PrivilegedExceptionAction<Object>() {
                @Nullable
                @Override
                public Object run() throws Exception {
                    doCreateDirectories(path);
                    return null;
                }
            });
        } catch (PrivilegedActionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            throw new RuntimeException(cause);
        }
    }

    private static void doCreateDirectories(Path path) throws IOException {
        if (!Files.exists(path)) {
            Files.createDirectories(path);
        }
    }

}
