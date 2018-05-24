/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 the original author or authors
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
 */
public interface Span extends AutoCloseable {

    /**
     * The name of the span.
     *
     * @param name the name of the span
     */
    void setName(String name);

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
     *
     * @param type the type of the span
     */
    void setType(String type);

    /**
     * Ends the span.
     * If the span has already ended, nothing happens.
     * <p>
     * As Span also implements the `java.lang.AutoCloseable` interface,
     * you can use it in try-with-resource blocks.
     * See {@link ElasticApm#startSpan()}.
     * </p>
     */
    void end();

    /**
     * An alias for {@link #end()} to make a {@link Span} work in try-with-resources statements.
     */
    @Override
    void close();
}
