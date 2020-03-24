/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2020 Elastic and contributors
 * %%
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
 * #L%
 */
package co.elastic.apm.agent.bootstrap;

import co.elastic.apm.agent.annotation.NonnullApi;
import co.elastic.apm.agent.bci.MethodHandleDispatcherHolder;
import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * This class lives in the bootstrap classloader so it's visible to all classes.
 * It is used to register method handles in order to call methods of classes that live in another classloader hierarchy.
 * This is the only class that needs
 */
@NonnullApi
public class MethodHandleDispatcher {

    /**
     * If the value was not weakly referenced,
     * this would keep the {@link MethodHandle}s alive,
     * which would keep the corresponding helper class alive,
     * which would keep the helper classloader alive,
     * which would keep the application class loader alive.
     * This would lead to a class loader leak if the web application is undeployed (all classes of the web app can't be unloaded).
     * <pre>
     *   Bootstrap CL ←─────────────────────── Agent CL
     *       ↑ - {@link MethodHandleDispatcher} ↑
     *       │  - WeakConcurrentMap<ClassLoader,│WeakReference<ConcurrentMap<String, MethodHandle>>>
     *     Ext/Platform CL           ╷          │                                     ╷
     *       ↑                       ╷          │                                     ╷
     *     System CL                 ╷          │                                     ╷
     *       ↑                       ╷          │                                     ╷
     *     Common                    ╷          │                                     ╷
     *     ↑    ↑                    ╷          │                                     ╷
     * WebApp1  WebApp2 ←╶╶╶╶╶╶╶╶╶╶╶╶┘          │                                     ╷
     *          ↑ - {@link MethodHandleDispatcherHolder}                              ╷
     *          │   - ConcurrentMap<String, MethodHandle>                             ╷
     *          │                               │    │                                ╷
     *          └─────────────────────── Helper CL   ↓                                ╷
     *                                    - HelperClass ←╶╶╶╶╶╶╶╶╶╶╶╶╶╶╶╶╶╶╶╶╶╶╶╶╶╶╶╶╶┘
     * Legend:
     *  ╶╶ weak reference
     *  ── strong reference
     * </pre>
     */
    private static WeakConcurrentMap<ClassLoader, WeakReference<ConcurrentMap<String, MethodHandle>>> dispatcherByClassLoader = new WeakConcurrentMap.WithInlinedExpunction<>();
    private static ConcurrentMap<String, MethodHandle> bootstrapDispatcher = new ConcurrentHashMap<>();

    /**
     * Calls {@link ConcurrentMap#clear()} on all injected {@link MethodHandleDispatcherHolder}s.
     * <p>
     * This should make all helper class loaders eligible for GC.
     * That is because the {@link java.lang.invoke.MethodHandle}s registered in the {@link MethodHandleDispatcherHolder} should be the only
     * references to the classes loaded by the helper class loader.
     * </p>
     * <p>
     * As the helper class loaders are (ideally) the only references that keep the Agent CL alive,
     * this also makes the Agent CL eligible for CL.
     * </p>
     */
    public synchronized static void clear() {
        for (Map.Entry<ClassLoader, WeakReference<ConcurrentMap<String, MethodHandle>>> entry : dispatcherByClassLoader) {
            ConcurrentMap<String, MethodHandle> dispatcher = entry.getValue().get();
            if (dispatcher != null) {
                dispatcher.clear();
            }
        }
        dispatcherByClassLoader.clear();
        bootstrapDispatcher.clear();
    }

    /**
     * Gets a {@link MethodHandle}, mostly referring to a static helper method called from an advice method.
     * <p>
     * An advice method is a static method that is annotated with {@link net.bytebuddy.asm.Advice.OnMethodEnter}
     * or {@link net.bytebuddy.asm.Advice.OnMethodExit}.
     * </p>
     * <p>
     * Helper methods are classloader specific so that they are able to reference types specific to that class loader.
     * An example would be a callback interface like {@code okhttp3.Callback}.
     * When there are multiple web applications deployed to the same servlet container, they might all use different versions of OkHttp.
     * </p>
     *
     * @param classOfTargetClassLoader
     * @param methodHandleName
     * @return
     */
    public static MethodHandle getMethodHandle(Class<?> classOfTargetClassLoader, String methodHandleName) {
        ConcurrentMap<String, MethodHandle> dispatcherForClassLoader = getDispatcherForClassLoader(classOfTargetClassLoader.getClassLoader());
        if (dispatcherForClassLoader != null) {
            MethodHandle methodHandle = dispatcherForClassLoader.get(methodHandleName);
            if (methodHandle != null) {
                return methodHandle;
            }
        }
        throw new IllegalArgumentException("No method handle found for " + methodHandleName);
    }

    public synchronized static void setDispatcherForClassLoader(ClassLoader classLoader, ConcurrentMap<String, MethodHandle> dispatcherMap) {
        dispatcherByClassLoader.put(classLoader, new WeakReference<>(dispatcherMap));
    }

    @Nullable
    public static ConcurrentMap<String, MethodHandle> getDispatcherForClassLoader(@Nullable ClassLoader classLoader) {
        if (classLoader == null) {
            return bootstrapDispatcher;
        }
        WeakReference<ConcurrentMap<String, MethodHandle>> reference = dispatcherByClassLoader.get(classLoader);
        if (reference != null) {
            return reference.get();
        }
        return null;
    }

}
