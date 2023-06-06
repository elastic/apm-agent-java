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
package co.elastic.apm.agent.bci.modules;

import co.elastic.apm.agent.sdk.internal.InternalAgentClass;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.apache.commons.compress.utils.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ModuleOpenerTest {

    @Test
    @EnabledForJreRange(min = JRE.JAVA_17)
    public void verifyModuleOpening() throws Exception {
        String illegalAccessorName = IllegalAccessor.class.getName();

        ClassLoader cl1 = new ChildFirstCopyClassloader(getClass().getClassLoader(), illegalAccessorName);
        Method illegal1 = cl1.loadClass(illegalAccessorName).getMethod("doAccess");
        ClassLoader cl2 = new ChildFirstCopyClassloader(getClass().getClassLoader(), illegalAccessorName);
        Method illegal2 = cl2.loadClass(illegalAccessorName).getMethod("doAccess");

        assertThatThrownBy(() -> illegal1.invoke(null))
            .hasCauseInstanceOf(IllegalAccessException.class);

        Instrumentation instr = ByteBuddyAgent.install();
        Class<?> classFromModuleToOpen = Class.forName("com.sun.jndi.ldap.LdapResult");
        ModuleOpener.getInstance().openModuleTo(instr, classFromModuleToOpen, cl2, Collections.singleton("com.sun.jndi.ldap"));

        //Access should be legal now and not throw any exception
        illegal2.invoke(null);
    }

    public static class IllegalAccessor {

        public static void doAccess() throws Exception {
            //com.sun.jndi.ldap.LdapResult is not accessible according to module rules
            Class.forName("com.sun.jndi.ldap.LdapResult").getConstructor().newInstance();
        }

    }

    private static class ChildFirstCopyClassloader extends ClassLoader implements InternalAgentClass {

        String childFirstClassName;

        public ChildFirstCopyClassloader(ClassLoader parent, String childFirstClassName) {
            super(parent);
            this.childFirstClassName = childFirstClassName;
        }

        @Override
        public String getMarker() {
            return CLASS_LOADER;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                if (childFirstClassName.equals(name)) {
                    Class<?> c = findLoadedClass(name);
                    if (c == null) {
                        try {
                            String binaryName = name.replace('.', '/') + ".class";
                            InputStream resourceAsStream = getParent().getResourceAsStream(binaryName);
                            if (resourceAsStream == null) {
                                throw new IllegalStateException(binaryName + " not found in parent classloader!");
                            }
                            byte[] bytecode = IOUtils.toByteArray(resourceAsStream);
                            c = defineClass(name, bytecode, 0, bytecode.length);
                            if (resolve) {
                                resolveClass(c);
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    return c;
                } else {
                    return super.loadClass(name, resolve);
                }
            }
        }
    }


}
