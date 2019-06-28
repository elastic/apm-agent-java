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
package co.elastic.apm.agent.hibernate.search.v5_x;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.hibernate.search.DeleteFileVisitor;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.hibernate.ScrollableResults;
import org.hibernate.Session;
import org.hibernate.search.FullTextSession;
import org.hibernate.search.jpa.FullTextEntityManager;
import org.hibernate.search.jpa.FullTextQuery;
import org.hibernate.search.jpa.Search;
import org.hibernate.search.query.dsl.BooleanJunction;
import org.hibernate.search.query.dsl.QueryBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;

import static co.elastic.apm.agent.hibernate.search.HibernateSearchAssertionHelper.assertApmSpanInformation;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HibernateSearch5InstrumentationTest extends AbstractInstrumentationTest {

    private static Path tempDirectory;

    private static EntityManagerFactory entityManagerFactory;

    private static EntityManager entityManager;

    @BeforeAll
    static void setUp() throws IOException {
        tempDirectory = Files.createTempDirectory("HibernateSearch5InstrumentationTest");
        entityManagerFactory = EntityManagerFactoryHelper.buildEntityManagerFactory(tempDirectory);
        entityManager = entityManagerFactory.createEntityManager();
        saveDogsToIndex();
    }

    @BeforeEach
    void initSingleTest() {
        tracer.startTransaction(TraceContext.asRoot(), null, null).activate();
    }

    @AfterEach
    void tearDownTest() {
        tracer.currentTransaction().deactivate().end();
    }

    @AfterAll
    static void tearDown() throws IOException {
        entityManager.close();
        entityManagerFactory.close();
        Files.walkFileTree(tempDirectory, new DeleteFileVisitor());
    }

    @Test
    void performLuceneIndexSearch() {
        FullTextEntityManager fullTextSession = Search.getFullTextEntityManager(entityManager);

        FullTextQuery ftq = fullTextSession.createFullTextQuery(createQueryForDog1(), Dog.class);

        List<Dog> result = (List<Dog>) ftq.getResultList();

        assertAll(() -> {
            assertEquals(1, result.size(), "Query result is not 1");
            assertEquals("dog1", result.get(0).getName(), "Result is not 'dog1'");
            assertApmSpanInformation(reporter, "name:dog1");
        });
    }

    @Test
    void performMultiResultLuceneIndexSearch() {
        FullTextEntityManager fullTextSession = Search.getFullTextEntityManager(entityManager);
        QueryBuilder builder = fullTextSession.getSearchFactory().buildQueryBuilder().forEntity(Dog.class).get();

        BooleanJunction<BooleanJunction> bool = builder.bool();
        bool.should(builder.keyword().wildcard().onField("name").matching("dog*").createQuery());

        Query query = bool.createQuery();

        FullTextQuery ftq = fullTextSession.createFullTextQuery(query, Dog.class);

        List<Dog> result = (List<Dog>) ftq.getResultList();

        assertAll(() -> {
            assertEquals(2, result.size(), "Query result is not 2");
            assertApmSpanInformation(reporter, "name:dog*");
        });
    }

    @Test
    void performScrollLuceneIndexSearch() {
        try (ScrollableResults scroll = createNonJpaFullTextQuery(createQueryForDog1()).scroll()) {
            assertTrue(scroll.next());

            assertAll(() -> {
                Object[] dogs = scroll.get();
                assertEquals(1, dogs.length, "The result does not contain 1 dog");
                assertEquals("dog1", ((Dog) dogs[0]).getName());
                assertTrue(scroll.isFirst());
                assertTrue(scroll.isLast());

                assertApmSpanInformation(reporter, "name:dog1");
            });
        }
    }

    @Test
    void performIteratorLuceneIndexSearch() {
        Iterator<Dog> iterate = (Iterator<Dog>) createNonJpaFullTextQuery(createQueryForDog1()).iterate();

        assertTrue(iterate.hasNext());
        final Dog dog = iterate.next();
        assertFalse(iterate.hasNext());

        assertAll(() -> {
            assertEquals("dog1", dog.getName());

            assertApmSpanInformation(reporter, "name:dog1");
        });
    }

    private Query createQueryForDog1() {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        builder.add(new BooleanClause(new TermQuery(new Term("name", "dog1")), BooleanClause.Occur.SHOULD));
        return builder.build();
    }

    private static void saveDogsToIndex() {
        entityManager.getTransaction().begin();
        Dog dog1 = new Dog("dog1");
        Dog dog2 = new Dog("dog2");
        entityManager.persist(dog1);
        entityManager.persist(dog2);
        entityManager.getTransaction().commit();
    }

    private org.hibernate.search.FullTextQuery createNonJpaFullTextQuery(Query query) {
        FullTextSession fullTextSession = org.hibernate.search.Search.getFullTextSession(entityManager.unwrap(Session.class));

        return fullTextSession.createFullTextQuery(query, Dog.class);
    }
}
