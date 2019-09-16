/*-
 * #%L
 * Elastic APM Java agent
 * %%
 * Copyright (C) 2018 - 2019 Elastic and contributors
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
package co.elastic.apm.agent.mongoclient;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.junit.After;
import org.junit.Before;
import org.testcontainers.containers.GenericContainer;

public class AbstractMongoClientInstrumentationTest extends AbstractInstrumentationTest {

    @SuppressWarnings("NullableProblems")
    protected static GenericContainer container;

    @Before
    public void startTransaction() {
        Transaction transaction = tracer.startTransaction(TraceContext.asRoot(), null, null).activate();
        transaction.withName("Mongo Transaction");
        transaction.withType("request");
        transaction.withResultIfUnset("success");
    }

    @After
    public void endTransaction() {
        try {
            Transaction currentTransaction = tracer.currentTransaction();
            if (currentTransaction != null) {
                currentTransaction.deactivate().end();
            }
        } finally {
            reporter.reset();
        }
    }
}

