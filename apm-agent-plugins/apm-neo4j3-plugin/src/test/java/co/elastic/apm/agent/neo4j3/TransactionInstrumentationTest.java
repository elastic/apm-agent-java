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

import org.junit.Test;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.GraphDatabase;
import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;
import org.neo4j.driver.v1.Transaction;
import org.neo4j.driver.v1.Values;

import co.elastic.apm.agent.impl.transaction.Span;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import java.util.stream.Collectors;

public class TransactionInstrumentationTest extends AbstractTransactionInstrumentationTest {

  @Test
  public void testTransaction_explicitSuccess() {
    try (Driver driver = GraphDatabase.driver(getDatabaseUrl())) {
      try (Session session = driver.session()) {
        try (Transaction tx = session.beginTransaction()) {
          tx.run("CREATE (a:Person {name: {x}})", Values.parameters("x", "Alice"));
          tx.run("CREATE (a:Person {name: {x}})", Values.parameters("x", "Bob"));
          StatementResult result = tx.run("MATCH (a:Person) WHERE a.name STARTS WITH {x} RETURN a.name AS name", Values.parameters("x", "A"));

          // check that the MATCH returns the expected result
          assertTrue(result.hasNext());
          Record record = result.next();
          assertThat(record.asMap()).containsOnlyKeys("name");
          assertThat(record.get("name").asString()).isEqualTo("Alice");
          assertFalse(result.hasNext());

          // explicitly mark the transaction successfully completed
          tx.success();
        }
      }
    }

    assertThat(reporter.getSpans()).hasSize(3);
    assertThat(reporter.getSpans().stream().map(Span::getNameAsString).collect(Collectors.toList()))
        .containsExactly("CREATE", "CREATE", "MATCH");
    assertThat(reporter.getSpans().stream().map(elt -> elt.getContext().getDb().getStatement()).collect(Collectors.toList()))
        .containsExactly("CREATE (a:Person {name: {x}})",
                         "CREATE (a:Person {name: {x}})",
                         "MATCH (a:Person) WHERE a.name STARTS WITH {x} RETURN a.name AS name");
  }

  @Test
  public void testTransaction_implicit() {
    try (Driver driver = GraphDatabase.driver(getDatabaseUrl())) {
      try (Session session = driver.session()) {
        StatementResult result = session.run(
          "MATCH (a:Person) WHERE a.name STARTS WITH {x} RETURN a.name AS name",
              Values.parameters("x", "A"));
        while (result.hasNext()) {
          Record record = result.next();
          System.out.println(record.get("name").asString());
        }
      }
    }
    assertThat(reporter.getSpans()).hasSize(1);
    assertThat(reporter.getFirstSpan().getNameAsString()).isEqualTo("CREATE");
    assertThat(reporter.getFirstSpan().getContext().getDb().getStatement())
        .isEqualTo("MATCH (a:Person) WHERE a.name STARTS WITH {x} RETURN a.name AS name");
  }

}
