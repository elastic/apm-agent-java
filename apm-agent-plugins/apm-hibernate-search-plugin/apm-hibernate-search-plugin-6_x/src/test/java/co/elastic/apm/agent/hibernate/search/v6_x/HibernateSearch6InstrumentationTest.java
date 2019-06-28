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
package co.elastic.apm.agent.hibernate.search.v6_x;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.hibernate.search.DeleteFileVisitor;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.session.SearchSession;
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
import java.util.List;

import static co.elastic.apm.agent.hibernate.search.HibernateSearchAssertionHelper.assertApmSpanInformation;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HibernateSearch6InstrumentationTest extends AbstractInstrumentationTest {

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
        SearchSession searchSession = Search.getSearchSession(entityManager);
        List<Dog> result = searchSession.search(Dog.class)
            .predicate(f -> f.match().onField("name").matching("dog1"))
            .fetchHits();

        assertAll(() -> {
            assertEquals(1, result.size(), "Query result is not 1");
            assertEquals("dog1", result.get(0).getName(), "Result is not 'dog1'");
            assertApmSpanInformation(reporter, "+name:dog1 #__HSEARCH_type:main");
        });
    }

    @Test
    void performHitCountLuceneIndexSearch() {
        SearchSession searchSession = Search.getSearchSession(entityManager);
        long resultCount = searchSession.search(Dog.class)
            .predicate(f -> f.match().onField("name").matching("dog1"))
            .fetchTotalHitCount();

        assertAll(() -> {
            assertEquals(1, resultCount, "Query hit count is not 1");
            assertApmSpanInformation(reporter, "+name:dog1 #__HSEARCH_type:main");
        });
    }

    @Test
    void performMultiResultLuceneIndexSearch() {
        SearchSession searchSession = Search.getSearchSession(entityManager);
        List<Dog> result = searchSession.search(Dog.class)
            .predicate(f -> f.wildcard().onField("name").matching("dog*"))
            .fetchHits();

        assertAll(() -> {
            assertEquals(2, result.size(), "Query result is not 2");
            assertApmSpanInformation(reporter, "+name:dog* #__HSEARCH_type:main");
        });
    }

    private static void saveDogsToIndex() {
        entityManager.getTransaction().begin();
        Dog dog1 = new Dog("dog1");
        Dog dog2 = new Dog("dog2");
        entityManager.persist(dog1);
        entityManager.persist(dog2);
        entityManager.getTransaction().commit();
    }

}
