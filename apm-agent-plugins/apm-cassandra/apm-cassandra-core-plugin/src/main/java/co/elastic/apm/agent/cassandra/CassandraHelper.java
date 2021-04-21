/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2021 Elastic and contributors
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
package co.elastic.apm.agent.cassandra;

import co.elastic.apm.agent.db.signature.SignatureParser;
import co.elastic.apm.agent.impl.Tracer;
import co.elastic.apm.agent.impl.transaction.AbstractSpan;
import co.elastic.apm.agent.impl.transaction.Span;

import javax.annotation.Nullable;

public class CassandraHelper {
    private static final String CASSANDRA = "cassandra";
    private final Tracer tracer;
    private final SignatureParser signatureParser = new SignatureParser();

    public CassandraHelper(Tracer tracer) {
        this.tracer = tracer;
    }

    @Nullable
    public Span startCassandraSpan(@Nullable String query, boolean preparedStatement) {
        Span span = tracer.createExitChildSpan();
        if (span == null) {
            return null;
        }
        span.activate()
            .withType("db")
            .withSubtype(CASSANDRA);
        span.getContext().getDb().withStatement(query);
        StringBuilder name = span.getAndOverrideName(AbstractSpan.PRIO_DEFAULT);
        if (query != null && name != null) {
            signatureParser.querySignature(query, name, preparedStatement);
        }
        span.withName(CASSANDRA, AbstractSpan.PRIO_DEFAULT - 1);

        span.getContext()
            .getDestination()
            .getService()
            .withType("db")
            .withResource(CASSANDRA)
            .withName(CASSANDRA);
        return span;
    }
}
