package co.elastic.apm.plugin.api;

import co.elastic.apm.AbstractInstrumentationTest;
import co.elastic.apm.api.ElasticApm;
import co.elastic.apm.api.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;


class TransactionInstrumentationTest extends AbstractInstrumentationTest {

    private Transaction transaction;

    @BeforeEach
    void setUp() {
        transaction = ElasticApm.startTransaction();
        transaction.setType("default");
    }


    @Test
    void testSetName() {
        transaction.setName("foo");
        endTransaction();
        assertThat(reporter.getFirstTransaction().getName().toString()).isEqualTo("foo");
    }

    @Test
    void testSetType() {
        transaction.setType("foo");
        endTransaction();
        assertThat(reporter.getFirstTransaction().getType()).isEqualTo("foo");
    }

    @Test
    void testAddTag() {
        transaction.addTag("foo", "bar");
        endTransaction();
        assertThat(reporter.getFirstTransaction().getContext().getTags()).containsEntry("foo", "bar");
    }

    @Test
    void testSetUser() {
        transaction.setUser("foo", "bar", "baz");
        endTransaction();
        assertThat(reporter.getFirstTransaction().getContext().getUser().getId()).isEqualTo("foo");
        assertThat(reporter.getFirstTransaction().getContext().getUser().getEmail()).isEqualTo("bar");
        assertThat(reporter.getFirstTransaction().getContext().getUser().getUsername()).isEqualTo("baz");
    }

    private void endTransaction() {
        transaction.end();
        assertThat(reporter.getTransactions()).hasSize(1);
    }
}
