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
package co.elastic.apm.agent.okhttp;

import co.elastic.apm.agent.tracer.Span;

/**
 * Used to create a wrapper for a callback or listener
 *
 * @param <T> the type of the wrapper to create
 */
public interface WrapperCreator<T> {

    /**
     * Wraps a callback or listener.
     * <p>
     * The implementation is supposed to create the actual wrapper which manages the lifecycle of the provided {@link Span}.
     * </p>
     *
     * @param delegate the actual callback which should be wrapped
     * @param span     the currently active span
     * @return the wrapped callback
     */
    T wrap(T delegate, Span<?> span);
}
