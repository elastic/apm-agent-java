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
package bootstrap.modulesetter;

import sun.misc.Unsafe;

import java.lang.reflect.Field;

/**
 * Overrides the module assigned to the class in with the {@code java.base} module.
 * <p>
 * NOTE: This class is compiled with Java 9 so it must only be loaded though reflection and only when running on Java 9.
 * In addition, since it relies on the {@link Unsafe} API, it must be loaded by the bootstrap or platform class loaders.
 * </p>
 * <p>
 * IMPORTANT: This class is relocated in a different package and stored as a classpath resource to be injected into bootstrap classloader.
 * A copy of this resource is stored in 'src/main/resources' and should be updated by running 'mvn clean package' whenever
 * this class is being modified. This has only an effect when running code/tests in the IDE as the resources are loaded
 * from the project classpath and not the packaged artifact.
 * </p>
 */

@SuppressWarnings("unused")
public class ModuleSetter {

    public static void setJavaBaseModule(Class<?> targetClass) throws Exception {

        Field moduleField = Class.class.getDeclaredField("module");
        if (moduleField.getType() == Module.class) {
            Module javaBaseModule = Class.class.getModule();
            Unsafe unsafe = Unsafe.getUnsafe();
            unsafe.putObject(targetClass, unsafe.objectFieldOffset(moduleField), javaBaseModule);
        } else {
            throw new IllegalStateException("Unexpected module field type: " + moduleField.getType().getName());
        }
    }
}
