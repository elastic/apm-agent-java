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
package co.elastic.apm.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotating a method with {@code @}{@link Traced} creates a {@link Span} as the child of the currently active span or transaction
 * ({@link ElasticApm#currentSpan()}).
 * <p>
 * When there is no current span,
 * a {@link Transaction} will be created instead.
 * </p>
 * <p>
 * Use this annotation over {@link CaptureSpan} or {@link CaptureTransaction} if a method can both be an entry point (a {@link Transaction})
 * or a unit of work within a transaction (a {@link Span}).
 * </p>
 * <p>
 * Note: it is required to configure the {@code application_packages}, otherwise this annotation will be ignored.
 * </p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Traced {

    /**
     * The name of the {@link Span} or {@link Transaction}.
     * Defaults to the {@code ClassName#methodName}
     */
    String value() default "";

    /**
     * <p>
     * Sets the general type of the captured span or transaction, used to group similar spans together, for example: `db`. Though there are no naming
     * restrictions for the general types, the following are standardized across all Elastic APM agents: `app`, `db`, `cache`,
     * `template`, and `ext`.
     * </p>
     * <p>
     * Defaults to {@code request} for transactions and {@code app} for spans
     * </p>
     */
    String type() default "";

    /**
     * <p>
     * Sets the subtype of the captured span, used to group similar spans together, for example: `mysql`.
     * </p>
     * <p>
     * NOTE: has no effect when a transaction is created
     * </p>
     */
    String subtype() default "";
    /**
     * <p>
     * Sets the action of the captured span, used to group similar spans together, for example: `query`.
     * </p>
     * <p>
     * NOTE: has no effect when a transaction is created
     * </p>
     */
    String action() default "";

    /**
     * <p>
     * By default, spans are discardable. This attribute is only relevant if the annotated method results with a span.
     * In some cases, spans may be discarded, for example if {@code span_min_duration} config option is set and the span does not exceed
     * the configured threshold. Set this attribute to {@code false} to make the current span non-discardable.
     * </p>
     * <p>
     * NOTE: making a span non-discardable implicitly makes the entire stack of active spans non-discardable as well. Child spans can still
     * be discarded.
     * </p>
     */
    boolean discardable() default true;
}
