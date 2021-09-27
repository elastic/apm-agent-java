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
package co.elastic.apm.agent.sdk.weakmap;

import javax.annotation.Nullable;

/**
 * Similar to {@link ThreadLocal} but without the risk of introducing class loader leaks.
 * A {@link ThreadLocal} can introduce leaks as it stores the values within {@link Thread} instances, which are loaded by the bootstrap CL.
 * This creates a reference chain that can keep the agent class loader or a webapp class loader alive.
 * <p>
 * In contrast to that, implementations of this interface are built upon a map that uses a
 * {@linkplain java.lang.ref.WeakReference weakly referenced} {@link Thread} instance as a key.
 * This avoids leaking a reference to the defining class loader of the class instances stored via the {@link #set} method.
 * </p>
 */
public interface DetachedThreadLocal<T> {

    @Nullable
    T get();

    @Nullable
    T getAndRemove();

    void set(T value);

    void remove();

}
