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
package co.elastic.apm.agent.hibernatesearch;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import org.hibernate.search.engine.search.query.SearchResult;
import org.hibernate.search.engine.search.query.dsl.SearchQueryOptionsStep;
import org.hibernate.search.engine.search.query.dsl.SearchQuerySelectStep;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.common.EntityReference;
import org.hibernate.search.mapper.orm.search.loading.dsl.SearchLoadingOptionsStep;
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
import java.util.Optional;

import static co.elastic.apm.agent.hibernatesearch.HibernateSearchAssertionHelper.assertApmSpanInformation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class HibernateSearch6InstrumentationTest extends AbstractInstrumentationTest {

    private static Path tempDirectory;

    private static EntityManagerFactory entityManagerFactory;

    private static EntityManager entityManager;

    @BeforeAll
    static void setUp() throws IOException {
        tempDirectory = Files.createTempDirectory("HibernateSearch6InstrumentationTest");
        entityManagerFactory = EntityManagerFactoryHelper.buildEntityManagerFactory(tempDirectory);
        entityManager = entityManagerFactory.createEntityManager();
        saveDogsToIndex();
    }

    @BeforeEach
    void initSingleTest() {
        tracer.startRootTransaction(null).activate();
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
        List<Dog> result = createMatchNameSearch("dog1").fetchAllHits();

        assertAll(() -> {
            assertThat(result.size()).isEqualTo(1);
            assertThat(result.get(0).getName()).isEqualTo("dog1");
            assertApmSpanInformation(reporter, "+name:dog1", "fetchAllHits");
        });
    }

    @Test
    void performMultiResultLuceneIndexSearch() {
        List<Dog> result = createWildcardNameSearch().fetchAllHits();

        assertAll(() -> {
            assertThat(result.size()).isEqualTo(2);
            assertApmSpanInformation(reporter, "+name:dog*", "fetchAllHits");
        });
    }

    @Test
    void performMultiResultLuceneIndexSearchWithLimitedResult() {
        List<Dog> result = createWildcardNameSearch().fetchHits(1);

        assertAll(() -> {
            assertThat(result.size()).isEqualTo(1);
            assertThat(result.get(0).getName()).isEqualTo("dog1");
            assertApmSpanInformation(reporter, "+name:dog*", "fetchHits");
        });
    }

    @Test
    void performMultiResultLuceneIndexSearchWithLimitedResultAndOffset() {
        List<Dog> result = createWildcardNameSearch().fetchHits(1, 1);

        assertAll(() -> {
            assertThat(result.size()).isEqualTo(1);
            assertThat(result.get(0).getName()).isEqualTo("dog2");
            assertApmSpanInformation(reporter, "+name:dog*", "fetchHits");
        });
    }

    @Test
    void performHitCountLuceneIndexSearch() {
        long resultCount = createMatchNameSearch("dog1").fetchTotalHitCount();

        assertAll(() -> {
            assertThat(resultCount).isEqualTo(1);
            assertApmSpanInformation(reporter, "+name:dog1", "fetchTotalHitCount");
        });
    }

    @Test
    void performLuceneIndexSearchWithSearchResult() {
        SearchResult<Dog> result = createMatchNameSearch("dog1").fetchAll();

        assertAll(() -> {
            assertThat(result.hits().size()).isEqualTo(1);
            assertThat(result.total().hitCount()).isEqualTo(1);
            assertThat(result.hits().get(0).getName()).isEqualTo("dog1");
            assertApmSpanInformation(reporter, "+name:dog1", "fetchAll");
        });
    }

    @Test
    void performMultiResultLuceneIndexSearchWithSearchResult() {
        SearchResult<Dog> result = createWildcardNameSearch().fetchAll();

        assertAll(() -> {
            assertThat(result.hits().size()).isEqualTo(2);
            assertThat(result.total().hitCount()).isEqualTo(2);
            assertApmSpanInformation(reporter, "+name:dog*", "fetchAll");
        });
    }

    @Test
    void performMultiResultLuceneIndexSearchWithLimitedSearchResult() {
        SearchResult<Dog> result = createWildcardNameSearch().fetch(1);

        assertAll(() -> {
            assertThat(result.hits().size()).isEqualTo(1);
            assertThat(result.total().hitCount()).isEqualTo(2);
            assertThat(result.hits().get(0).getName()).isEqualTo("dog1");
            assertApmSpanInformation(reporter, "+name:dog*", "fetch");
        });
    }

    @Test
    void performMultiResultLuceneIndexSearchWithLimitedSearchResultAndOffset() {
        SearchResult<Dog> result = createWildcardNameSearch().fetch(1, 1);

        assertAll(() -> {
            assertThat(result.hits().size()).isEqualTo(1);
            assertThat(result.total().hitCount()).isEqualTo(2);
            assertThat(result.hits().get(0).getName()).isEqualTo("dog2");
            assertApmSpanInformation(reporter, "+name:dog*", "fetch");
        });
    }

    @Test
    void performSingleHitLuceneIndexSearchWithResult()    {
        Optional<Dog> result = createMatchNameSearch("dog1").fetchSingleHit();

        assertThat(result.isPresent()).isTrue();
        assertAll(() -> {
            assertThat(result.get().getName()).isEqualTo("dog1");
            assertApmSpanInformation(reporter, "+name:dog1", "fetchSingleHit");
        });
    }

    @Test
    void performSingleHitLuceneIndexSearchWithoutResult() {
        Optional<Dog> result = createMatchNameSearch("dog1234").fetchSingleHit();

        assertThat(result.isPresent()).isFalse();
        assertAll(() -> assertApmSpanInformation(reporter, "+name:dog1234", "fetchSingleHit"));
    }

    private SearchQueryOptionsStep<?, Dog, SearchLoadingOptionsStep, ?, ?> createMatchNameSearch(final String dogName) {
        return createDogSearch()
            .where(f -> f.match().field("name").matching(dogName));
    }

    private SearchQueryOptionsStep<?, Dog, SearchLoadingOptionsStep, ?, ?> createWildcardNameSearch() {
        return createDogSearch()
            .where(f -> f.wildcard().field("name").matching("dog*"))
            .sort(searchSortFactory -> searchSortFactory.field("name").asc());
    }

    private SearchQuerySelectStep<?, EntityReference, Dog, SearchLoadingOptionsStep, ?, ?> createDogSearch() {
        return Search.session(entityManager)
            .search(Dog.class);
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
