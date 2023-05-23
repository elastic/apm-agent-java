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
package co.elastic.apm.agent.cassandra;

import co.elastic.apm.agent.db.signature.SignatureParser;
import co.elastic.apm.agent.tracer.AbstractSpan;
import co.elastic.apm.agent.tracer.Span;
import co.elastic.apm.agent.tracer.Tracer;

import javax.annotation.Nullable;

public class CassandraHelper {
    private static final String CASSANDRA = "cassandra";
    private final Tracer tracer;
    private final SignatureParser signatureParser = new SignatureParser();

    public CassandraHelper(Tracer tracer) {
        this.tracer = tracer;
    }

    @Nullable
    public Span<?> startCassandraSpan(@Nullable String query, boolean preparedStatement, @Nullable String keyspace) {
        AbstractSpan<?> active = tracer.getActive();
        if (active == null) {
            return null;
        }
        Span<?> span = active.createExitSpan();
        if (span == null) {
            return null;
        }
        span.activate()
            .withType("db")
            .withSubtype(CASSANDRA);

        span.getContext().getDb()
            .withStatement(query)
            .withInstance(keyspace);

        StringBuilder name = span.getAndOverrideName(AbstractSpan.PRIORITY_DEFAULT);
        if (query != null && name != null) {
            signatureParser.querySignature(query, name, preparedStatement);
        }
        span.withName(CASSANDRA, AbstractSpan.PRIORITY_DEFAULT - 1);

        span.getContext().getServiceTarget()
            .withType(CASSANDRA)
            .withName(keyspace);

        return span;
    }
}
