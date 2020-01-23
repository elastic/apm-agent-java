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
package co.elastic.apm.opentracing;

import io.opentracing.tag.StringTag;

public class ElasticApmTags {

    /**
     * Sets the type of the transaction or span.
     * Prior to version 1.4, span type was hierarchical, eg "db.postgresql.query". Since version 1.4, subtype (corresponding 'postresql'
     * in this example) and action (corresponding 'query' in this example) are set separately. Agents of version 1.4 and higher will parse
     * hierarchical type setting so not to break existing OpenTracing bridge usages, but this is deprecated and will be remove in future
     * versions.
     */
    public static final StringTag TYPE = new StringTag("type");

    /**
     * Sets the subtype of the span (irrelevant for transactions)
     */
    public static final StringTag SUBTYPE = new StringTag("subtype");

    /**
     * Sets the action related to the span (irrelevant for transactions)
     */
    public static final StringTag ACTION = new StringTag("action");

    /**
     * Sets the user id,
     * appears in the "User" tab in the transaction details in the Elastic APM UI
     */
    public static final StringTag USER_ID = new StringTag("user.id");

    /**
     * Sets the user email address,
     * appears in the "User" tab in the transaction details in the Elastic APM UI
     */
    public static final StringTag USER_EMAIL = new StringTag("user.email");

    /**
     * Sets the user ,
     * appears in the "User" tab in the transaction details in the Elastic APM UI
     */
    public static final StringTag USER_USERNAME = new StringTag("user.username");

    /**
     * Sets the result of the transaction.
     * Overrides the default value of {@code success}.
     * If the {@code error} tag is set to {@code true},
     * the default value is {@code error}.
     */
    public static final StringTag RESULT = new StringTag("result");

}
