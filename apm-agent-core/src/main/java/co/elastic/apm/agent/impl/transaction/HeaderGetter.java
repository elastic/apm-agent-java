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
package co.elastic.apm.agent.impl.transaction;

import javax.annotation.Nullable;

public interface HeaderGetter<T, C> {

    @Nullable
    T getFirstHeader(String headerName, C carrier);

    /**
     * Calls the consumer for each header value with the given key
     * until all entries have been processed or the action throws an exception.
     * <p>
     * The third parameter lets callers pass in a stateful object to be modified with header values,
     * so the {@link HeaderConsumer} implementation itself can be stateless and potentially reusable.
     * </p>
     *
     * @param headerName the name of the header
     * @param carrier    the object containing the headers
     * @param state      the object to be passed as the second parameter to each invocation on the specified consumer
     * @param consumer   the action to be performed for each header value
     * @param <S>        the type of the state object
     */
    <S> void forEach(String headerName, C carrier, S state, HeaderConsumer<T, S> consumer);

    interface HeaderConsumer<T, S> {
        void accept(@Nullable T headerValue, S state);
    }
}
