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
package co.elastic.apm.agent.neo4j3;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.testcontainers.containers.GenericContainer;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.Transaction;

public abstract class AbstractTransactionInstrumentationTest extends AbstractInstrumentationTest {

  protected static GenericContainer container;

  public static String getDatabaseUrl() {
    return "bolt://" + container.getContainerIpAddress() + ":" + container.getMappedPort(7687);
  }

  @BeforeClass
  public static void setupDatabase() {
    container = new GenericContainer("neo4j:3.4")
        .withExposedPorts(7687)
        .withEnv("NEO4J_AUTH", "none");
    container.start();
  }

  @AfterClass
  public static void teardownDatabase() {
    container.stop();
    container = null;
  }

  @Before
  public void startTransaction() throws Exception {
    Transaction transaction = tracer.startTransaction(TraceContext.asRoot(), null, null).activate();
    transaction.withName("Neo4j Transaction");
    transaction.withType("request");
    transaction.withResultIfUnset("success");
  }

  @After
  public void endTransaction() throws Exception {
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
