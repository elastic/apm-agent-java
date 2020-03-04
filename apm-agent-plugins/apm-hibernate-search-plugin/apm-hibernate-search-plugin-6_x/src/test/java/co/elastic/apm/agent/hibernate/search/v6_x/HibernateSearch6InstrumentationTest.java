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
package co.elastic.apm.agent.hibernate.search.v6_x;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.hibernate.search.DeleteFileVisitor;
import org.hibernate.search.engine.search.dsl.query.SearchQueryContext;
import org.hibernate.search.engine.search.query.SearchResult;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.search.dsl.query.HibernateOrmSearchQueryResultDefinitionContext;
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

import static co.elastic.apm.agent.hibernate.search.HibernateSearchAssertionHelper.assertApmSpanInformation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

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
        List<Dog> result = createMatchNameSearch("dog1").fetchHits();

        assertAll(() -> {
            assertThat(result.size()).isEqualTo(1);
            assertThat(result.get(0).getName()).isEqualTo("dog1");
            assertApmSpanInformation(reporter, "+name:dog1 #__HSEARCH_type:main", "fetchHits");
        });
    }

    @Test
    void performMultiResultLuceneIndexSearch() {
        List<Dog> result = createWildcardNameSearch().fetchHits();

        assertAll(() -> {
            assertThat(result.size()).isEqualTo(2);
            assertApmSpanInformation(reporter, "+name:dog* #__HSEARCH_type:main", "fetchHits");
        });
    }

    @Test
    void performMultiResultLuceneIndexSearchWithLimitedResult() {
        List<Dog> result = createWildcardNameSearch().fetchHits(1);

        assertAll(() -> {
            assertThat(result.size()).isEqualTo(1);
            assertThat(result.get(0).getName()).isEqualTo("dog1");
            assertApmSpanInformation(reporter, "+name:dog* #__HSEARCH_type:main", "fetchHits");
        });
    }

    @Test
    void performMultiResultLuceneIndexSearchWithLimitedResultAndOffset() {
        List<Dog> result = createWildcardNameSearch().fetchHits(1, 1);

        assertAll(() -> {
            assertThat(result.size()).isEqualTo(1);
            assertThat(result.get(0).getName()).isEqualTo("dog2");
            assertApmSpanInformation(reporter, "+name:dog* #__HSEARCH_type:main", "fetchHits");
        });
    }

    @Test
    void performHitCountLuceneIndexSearch() {
        long resultCount = createMatchNameSearch("dog1").fetchTotalHitCount();

        assertAll(() -> {
            assertThat(resultCount).isEqualTo(1);
            assertApmSpanInformation(reporter, "+name:dog1 #__HSEARCH_type:main", "fetchTotalHitCount");
        });
    }

    @Test
    void performLuceneIndexSearchWithSearchResult() {
        SearchResult<Dog> result = createMatchNameSearch("dog1").fetch();

        assertAll(() -> {
            assertThat(result.getHits().size()).isEqualTo(1);
            assertThat(result.getTotalHitCount()).isEqualTo(1);
            assertThat(result.getHits().get(0).getName()).isEqualTo("dog1");
            assertApmSpanInformation(reporter, "+name:dog1 #__HSEARCH_type:main", "fetch");
        });
    }

    @Test
    void performMultiResultLuceneIndexSearchWithSearchResult() {
        SearchResult<Dog> result = createWildcardNameSearch().fetch();

        assertAll(() -> {
            assertThat(result.getHits().size()).isEqualTo(2);
            assertThat(result.getTotalHitCount()).isEqualTo(2);
            assertApmSpanInformation(reporter, "+name:dog* #__HSEARCH_type:main", "fetch");
        });
    }

    @Test
    void performMultiResultLuceneIndexSearchWithLimitedSearchResult() {
        SearchResult<Dog> result = createWildcardNameSearch().fetch(1);

        assertAll(() -> {
            assertThat(result.getHits().size()).isEqualTo(1);
            assertThat(result.getTotalHitCount()).isEqualTo(2);
            assertThat(result.getHits().get(0).getName()).isEqualTo("dog1");
            assertApmSpanInformation(reporter, "+name:dog* #__HSEARCH_type:main", "fetch");
        });
    }

    @Test
    void performMultiResultLuceneIndexSearchWithLimitedSearchResultAndOffset() {
        SearchResult<Dog> result = createWildcardNameSearch().fetch(1, 1);

        assertAll(() -> {
            assertThat(result.getHits().size()).isEqualTo(1);
            assertThat(result.getTotalHitCount()).isEqualTo(2);
            assertThat(result.getHits().get(0).getName()).isEqualTo("dog2");
            assertApmSpanInformation(reporter, "+name:dog* #__HSEARCH_type:main", "fetch");
        });
    }

    @Test
    void performSingleHitLuceneIndexSearchWithResult()    {
        Optional<Dog> result = createMatchNameSearch("dog1").fetchSingleHit();

        assertThat(result.isPresent()).isTrue();
        assertAll(() -> {
            assertThat(result.get().getName()).isEqualTo("dog1");
            assertApmSpanInformation(reporter, "+name:dog1 #__HSEARCH_type:main", "fetchSingleHit");
        });
    }

    @Test
    void performSingleHitLuceneIndexSearchWithoutResult() {
        Optional<Dog> result = createMatchNameSearch("dog1234").fetchSingleHit();

        assertThat(result.isPresent()).isFalse();
        assertAll(() -> assertApmSpanInformation(reporter, "+name:dog1234 #__HSEARCH_type:main", "fetchSingleHit"));
    }

    private SearchQueryContext<?, Dog, ?> createMatchNameSearch(final String dogName) {
        return createDogSearch()
            .predicate(f -> f.match().onField("name").matching(dogName));
    }

    private SearchQueryContext<?, Dog, ?> createWildcardNameSearch() {
        return createDogSearch()
            .predicate(f -> f.wildcard().onField("name").matching("dog*"));
    }

    private HibernateOrmSearchQueryResultDefinitionContext<Dog> createDogSearch() {
        return Search.getSearchSession(entityManager)
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
