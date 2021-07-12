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
package co.elastic.apm.agent.lookup;

import net.bytebuddy.dynamic.loading.ByteArrayClassLoader;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MethodHandleLookupTest {

    @Test
    void name() throws Throwable {
        Map<String, byte[]> typeDefinitions = Map.of(
            Foo.class.getName(), Foo.class.getResourceAsStream("Foo.class").readAllBytes(),
            Bar.class.getName(), Bar.class.getResourceAsStream("Bar.class").readAllBytes()
        );

        // MethodHandles.publicLookup() always succeeds on the first invocation
        lookupAndInvoke(new ByteArrayClassLoader(null, typeDefinitions, ByteArrayClassLoader.PersistenceHandler.MANIFEST));
        // MethodHandles.publicLookup() fails on the second invocation,
        // even though the classes are loaded from an isolated class loader hierarchy
        lookupAndInvoke(new ByteArrayClassLoader(null, typeDefinitions, ByteArrayClassLoader.PersistenceHandler.MANIFEST));
    }

    private void lookupAndInvoke(ClassLoader classLoader) throws Throwable {
        Class<?> fooClass = classLoader.loadClass(Foo.class.getName());
        MethodHandles.Lookup lookup;
        // using public lookup fails with LinkageError on second invocation - is this a (known) JVM bug?
        // lookup = MethodHandles.publicLookup();
        lookup = (MethodHandles.Lookup) fooClass.getMethod("getLookup").invoke(null);
        MethodHandle methodHandle = lookup
            .findStatic(fooClass, "foo", MethodType.methodType(String.class, classLoader.loadClass(Bar.class.getName())));
        assertThat(methodHandle.invoke((Bar) null)).isEqualTo("foo");
    }

}
