package co.elastic.apm.agent.hibernate.search.v6_x;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.hibernate.search.DeleteFileVisitor;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HibernateSearch6InstrumentationTest extends AbstractInstrumentationTest {

    private Path tempDirectory;

    private EntityManagerFactory entityManagerFactory;

    private EntityManager entityManager;

    @BeforeEach
    void setUp() throws IOException {
        tempDirectory = Files.createTempDirectory("HibernateSearch5InstrumentationTest");
        entityManagerFactory = EntityManagerFactoryHelper.buildEntityManagerFactory(tempDirectory);
        entityManager = entityManagerFactory.createEntityManager();
        tracer.startTransaction(TraceContext.asRoot(), null, null).activate();
    }

    @AfterEach
    void tearDown() throws IOException {
        tracer.currentTransaction().deactivate().end();
        entityManager.close();
        entityManagerFactory.close();
        Files.walkFileTree(tempDirectory, new DeleteFileVisitor());
    }

    @Test
    public void performLuceneIndexSearch() {
        saveDogsToIndex();

        SearchSession searchSession = Search.getSearchSession(entityManager);
        List<Dog> result = searchSession.search(Dog.class)
            .predicate(f -> f.match().onField("name").matching("dog1"))
            .fetchHits();

        assertAll(() -> {
            assertEquals(1, result.size(), "Query result is not 1");
            assertEquals("dog1", result.get(0).getName(), "Result is not 'dog1'");
            assertEquals(1, reporter.getSpans().size(), "Didn't find 1 span");
            assertEquals("hibernate-search", reporter.getFirstSpan().getSubtype(),
                "Subtype of span is not 'hibernate-search'");
            assertEquals("+name:dog1 #__HSEARCH_type:main", reporter.getFirstSpan().getContext().getDb().getStatement(),
                "Statement is not '+name:dog1 #__HSEARCH_type:main'");
        });
    }

    @Test
    public void performMutiResultLuceneIndexSearch() {
        saveDogsToIndex();

        SearchSession searchSession = Search.getSearchSession(entityManager);
        List<Dog> result = searchSession.search(Dog.class)
            .predicate(f -> f.wildcard().onField("name").matching("dog*"))
            .fetchHits();

        assertAll(() -> {
            assertEquals(2, result.size(), "Query result is not 2");
            assertEquals(1, reporter.getSpans().size(), "Didn't find 1 span");
            assertEquals("hibernate-search", reporter.getFirstSpan().getSubtype(),
                "Subtype of span is not 'hibernate-search'");
            assertEquals("+name:dog* #__HSEARCH_type:main", reporter.getFirstSpan().getContext().getDb().getStatement(),
                "Statement is not '+name:dog1 #__HSEARCH_type:main'");
        });
    }

    private void saveDogsToIndex() {
        entityManager.getTransaction().begin();
        Dog dog1 = new Dog("dog1");
        Dog dog2 = new Dog("dog2");
        entityManager.persist(dog1);
        entityManager.persist(dog2);
        entityManager.getTransaction().commit();
    }

}
