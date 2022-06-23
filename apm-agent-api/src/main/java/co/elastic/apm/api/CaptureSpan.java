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
 * Annotating a method with {@code @}{@link CaptureSpan} creates a {@link Span} as the child of the currently active span or transaction
 * ({@link ElasticApm#currentSpan()}).
 * <p>
 * When there is no current span,
 * no span will be created.
 * </p>
 * <p>
 * Note: it is required to configure the {@code application_packages}, otherwise this annotation will be ignored.
 * </p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CaptureSpan {

    /**
     * The name of the {@link Span}.
     * Defaults to the {@code ClassName#methodName}
     */
    String value() default "";

    /**
     * <p>
     * Sets the general type of the captured span, used to group similar spans together, for example: `db`. Though there are no naming
     * restrictions for the general types, the following are standardized across all Elastic APM agents: `app`, `db`, `cache`,
     * `template`, and `ext`.
     * </p>
     * <p>
     * Prior to version 1.4, the typing system was hierarchical. For instance, all spans of MySQL queries were given the type
     * `db.mysql.query`. This is deprecated now and '.' (dot) character will not be allowed starting version 2.0. To get better
     * span aggregation capabilities, use the {@link #subtype()} and {@link #action()} in combination with type.
     * </p>
     */
    String type() default "app";

    /**
     * <p>
     * Sets the subtype of the captured span, used to group similar spans together, for example: `mysql`.
     * NOTE: current users of the hierarchical typing system are advised to use the second part of the type as subtype starting 1.4. For
     * example, if used `external.http.okhttp` before 1.4, set `okhttp` as subtype.
     * </p>
     */
    String subtype() default "";
    /**
     * <p>
     * Sets the action of the captured span, used to group similar spans together, for example: `query`.
     * NOTE: current users of the hierarchical typing system are advised to use the third part of the type as action starting 1.4. For
     * example, if used `template.jsf.render` before 1.4, set `render` as action.
     * </p>
     */
    String action() default "";

    /**
     * <p>
     * By default, spans are discardable.
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
