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
package co.elastic.apm.agent.bci;

import co.elastic.apm.agent.bootstrap.MethodHandleDispatcher;

import java.lang.invoke.MethodHandle;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A class that holds a static reference to method handler dispatcher for a specific class loader.
 * See also {@link MethodHandleDispatcher#dispatcherByClassLoader}
 * Used to be loaded by the parent of the class loader that loads the helper
 * itself, thus making the helper instance non-GC-eligible as long as the parent class loader is alive.
 * NOTE: THIS CLASS SHOULD NEVER BE INSTANTIATED NOR REFERENCED EXPLICITLY, IT SHOULD ONLY BE USED THROUGH REFLECTION
 */
public class MethodHandleDispatcherHolder {
    public static final ConcurrentMap<String, MethodHandle> registry = new ConcurrentHashMap<String, MethodHandle>();

    // should never be instanciated
    private MethodHandleDispatcherHolder() {
    }
}
