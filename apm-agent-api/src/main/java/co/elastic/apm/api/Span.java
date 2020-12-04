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
package co.elastic.apm.api;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A span contains information about a specific code path, executed as part of a {@link Transaction}.
 * <p>
 * If for example a database query happens within a recorded transaction,
 * a span representing this database query may be created.
 * In such a case the name of the span will contain information about the query itself,
 * and the type will hold information about the database type.
 * </p>
 * <p>
 * Call {@link ElasticApm#currentSpan()} to get a reference of the current span.
 * </p>
 * <p>
 * Note: Calling any methods after {@link #end()} has been called is illegal.
 * You may only interact with spans when you have control over its lifecycle.
 * For example, if a span is ended on another thread you must not add labels if there is a chance for a race between the {@link #end()}
 * and the {@link #addLabel(String, String)} method.
 * </p>
 */
public interface Span extends BaseSpan<Span> {

     /**
     * Appends destination service resource value
     *
     * @param resource - an appended destination service resource value
     * @return the current active span, never {@code null}
     * @since 1.19.1
     */
    @Nonnull
    Span withDestinationServiceResource(@Nullable String resource);

    /**
     * Appends destination service name value
     *
     * @param name - an appended destination service name value
     * @return the current active span, never {@code null}
     * @since 1.19.1
     */
    @Nonnull
    Span withDestinationServiceName(@Nullable String name);

    /**
     * Sets destination service type value
     *
     * @param type - a destination service type value
     * @return the current active span, never {@code null}
     * @since 1.19.1
     */
    @Nonnull
    Span setDestinationServiceType(@Nullable String type);
}
