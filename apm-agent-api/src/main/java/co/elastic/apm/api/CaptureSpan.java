/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018-2019 Elastic and contributors
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package co.elastic.apm.api;

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
public @interface CaptureSpan {

    /**
     * The name of the {@link Span}.
     * Defaults to the {@code ClassName#methodName}
     */
    String value() default "";

    /**
     * Sets the type of span.
     * <p>
     * The type is a hierarchical string used to group similar spans together.
     * For instance, all spans of MySQL queries are given the type `db.mysql.query`.
     * </p>
     * <p>
     * In the above example `db` is considered the type prefix. Though there are no naming restrictions for this prefix,
     * the following are standardized across all Elastic APM agents: `app`, `db`, `cache`, `template`, and `ext`.
     * </p>
     */
    String type() default "app";
}
