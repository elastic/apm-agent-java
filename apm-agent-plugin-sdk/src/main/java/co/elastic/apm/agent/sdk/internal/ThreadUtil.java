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

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.MethodCall;
import net.bytebuddy.matcher.ElementMatchers;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ThreadUtil {

    interface VirtualChecker {
        boolean isVirtual(Thread thread);
    }

    private static final VirtualChecker VIRTUAL_CHECKER = generateVirtualChecker();


    public static boolean isVirtual(Thread thread) {
        return VIRTUAL_CHECKER.isVirtual(thread);
    }

    /**
     * Generates a VirtualChecker based on the current JVM.
     * If the JVM does not support virtual threads, a VirtualChecker which always returns false is returned.
     * <p>
     * Otherwise we runtime generate an implementation which invokes Thread.isVirtual().
     * We use runtime proxy generation because Thread.isVirtual() has been added in Java 19 as preview and Java 21 as non preview.
     * Therefore we would require a compilation with Java 19 (non-LTS), because Java 20+ does not allow targeting Java 7.
     * <p>
     * Alternatively we could simply invoke Thread.isVirtual via reflection.
     * However, because this check can be used very frequently we want to avoid the penalty / missing inline capability of reflection.
     *
     * @return the implementation for {@link VirtualChecker}.
     */
    private static VirtualChecker generateVirtualChecker() {
        Method isVirtual = null;
        try {
            isVirtual = Thread.class.getMethod("isVirtual");
            isVirtual.invoke(Thread.currentThread()); //invoke to ensure it does not throw exceptions for preview versions
            Class<? extends VirtualChecker> impl = new ByteBuddy()
                .subclass(VirtualChecker.class)
                .method(ElementMatchers.named("isVirtual"))
                .intercept(MethodCall.invoke(isVirtual).onArgument(0))
                .make()
                .load(VirtualChecker.class.getClassLoader(), ClassLoadingStrategy.Default.INJECTION)
                .getLoaded();
            return impl.getConstructor().newInstance();
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            return new VirtualChecker() {
                @Override
                public boolean isVirtual(Thread thread) {
                    return false; //virtual threads are not supported, therefore no thread is virtual
                }
            };
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        }
    }
}
