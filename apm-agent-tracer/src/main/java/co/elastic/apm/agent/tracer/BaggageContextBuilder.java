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
package co.elastic.apm.agent.tracer;

import javax.annotation.Nullable;

/**
 * Builder for creating a context with alteredBaggage.
 */
public interface BaggageContextBuilder {

    /**
     * @param key   The key of the baggage to store
     * @param value the updated value. If null, any present value for the given key will be removed.
     */
    BaggageContextBuilder put(String key, @Nullable String value);

    /**
     * Same as invoking {@link #put(String, String)} with a null value.
     *
     * @param key
     */
    BaggageContextBuilder remove(String key);

    /**
     * @return the created context with the baggage updates applied.
     */
    TraceState<?> buildContext();
}
