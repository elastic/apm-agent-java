package co.elastic.apm.agent.hibernate.search.v5_x;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HibernateSearch5InstrumentationTest extends AbstractInstrumentationTest {

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

        FullTextEntityManager fullTextSession = Search.getFullTextEntityManager(entityManager);
        QueryBuilder builder = fullTextSession.getSearchFactory().buildQueryBuilder().forEntity(Dog.class).get();

        BooleanJunction<BooleanJunction> bool = builder.bool();
        bool.should(builder.keyword().onField("name").matching("dog1").createQuery());

        Query query = bool.createQuery();

        FullTextQuery ftq = fullTextSession.createFullTextQuery(query, Dog.class);

        List<Dog> result = (List<Dog>) ftq.getResultList();

        assertAll(() -> {
            assertEquals(1, result.size(), "Query result is not 1");
            assertEquals("dog1", result.get(0).getName(), "Result is not 'dog1'");
            assertEquals(1, reporter.getSpans().size(), "Didn't find 1 span");
            assertEquals("hibernate-search", reporter.getFirstSpan().getSubtype(),
                "Subtype of span is not 'hibernate-search'");
            assertEquals("name:dog1", reporter.getFirstSpan().getContext().getDb().getStatement(),
                "Statement is not 'name:dog1'");
        });
    }

    @Test
    public void performMultiResultLuceneIndexSearch() {
        saveDogsToIndex();

        FullTextEntityManager fullTextSession = Search.getFullTextEntityManager(entityManager);
        QueryBuilder builder = fullTextSession.getSearchFactory().buildQueryBuilder().forEntity(Dog.class).get();

        BooleanJunction<BooleanJunction> bool = builder.bool();
        bool.should(builder.keyword().wildcard().onField("name").matching("dog*").createQuery());

        Query query = bool.createQuery();

        FullTextQuery ftq = fullTextSession.createFullTextQuery(query, Dog.class);

        List<Dog> result = (List<Dog>) ftq.getResultList();

        assertAll(() -> {
            assertEquals(2, result.size(), "Query result is not 2");
            assertEquals(1, reporter.getSpans().size(), "Didn't find 1 span");
            assertEquals("hibernate-search", reporter.getFirstSpan().getSubtype(),
                "Subtype of span is not 'hibernate-search'");
            assertEquals("name:dog*", reporter.getFirstSpan().getContext().getDb().getStatement(),
                "Statement is not 'name:dog*'");
        });
    }

    @Test
    public void performScrollLuceneIndexSearch() {
        saveDogsToIndex();

        FullTextSession fullTextSession = org.hibernate.search.Search.getFullTextSession(entityManager.unwrap(Session.class));

        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        builder.add(new BooleanClause(new TermQuery(new Term("name", "dog1")), BooleanClause.Occur.SHOULD));
        Query query = builder.build();

        org.hibernate.search.FullTextQuery fullTextQuery = fullTextSession.createFullTextQuery(query, Dog.class);

        try (ScrollableResults scroll = fullTextQuery.scroll()) {
            assertTrue(scroll.next());

            assertAll(() -> {
                Object[] dogs = scroll.get();
                assertEquals(1, dogs.length, "The result does not contain 1 dog");
                assertEquals("dog1", ((Dog) dogs[0]).getName());
                assertTrue(scroll.isFirst());
                assertTrue(scroll.isLast());

                assertEquals(1, reporter.getSpans().size(), "Didn't find 1 span");
                assertEquals("hibernate-search", reporter.getFirstSpan().getSubtype(),
                    "Subtype of span is not 'hibernate-search'");
                assertEquals("name:dog1", reporter.getFirstSpan().getContext().getDb().getStatement(),
                    "Statement is not 'name:dog1'");
            });
        }
    }

    @Test
    public void performIteratorLuceneIndexSearch() {
        saveDogsToIndex();

        FullTextSession fullTextSession = org.hibernate.search.Search.getFullTextSession(entityManager.unwrap(Session.class));

        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        builder.add(new BooleanClause(new TermQuery(new Term("name", "dog1")), BooleanClause.Occur.SHOULD));
        Query query = builder.build();

        org.hibernate.search.FullTextQuery fullTextQuery = fullTextSession.createFullTextQuery(query, Dog.class);

        Iterator<Dog> iterate = (Iterator<Dog>) fullTextQuery.iterate();

        assertTrue(iterate.hasNext());
        final Dog dog = iterate.next();
        assertFalse(iterate.hasNext());

        assertAll(() -> {
            assertEquals("dog1", dog.getName());

            assertEquals(1, reporter.getSpans().size(), "Didn't find 1 span");
            assertEquals("hibernate-search", reporter.getFirstSpan().getSubtype(),
                "Subtype of span is not 'hibernate-search'");
            assertEquals("name:dog1", reporter.getFirstSpan().getContext().getDb().getStatement(),
                "Statement is not 'name:dog1'");
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
