/*
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *   http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package co.elastic.apm.agent.servlet;

import org.junit.function.ThrowingRunnable;

import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.function.Supplier;
import java.util.jar.JarFile;

public class CustomManifestLoader extends URLClassLoader {
    private final Supplier<InputStream> manifestSupplier;

    public CustomManifestLoader(Supplier<InputStream> manifestSupplier) {
        super(new URL[0]);
        this.manifestSupplier = manifestSupplier;
    }

    public static void withThreadContextClassLoader(ClassLoader contextClassLoader, ThrowingRunnable runnable) {
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(contextClassLoader);
            runnable.run();
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        if ((JarFile.MANIFEST_NAME).equals(name)) {
            return manifestSupplier.get();
        }
        return super.getResourceAsStream(name);
    }
}
