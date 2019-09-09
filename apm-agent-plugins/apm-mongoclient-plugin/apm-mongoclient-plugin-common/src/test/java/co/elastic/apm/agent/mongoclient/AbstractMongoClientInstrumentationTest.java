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
        Transaction currentTransaction = tracer.currentTransaction();
        if (currentTransaction != null) {
            currentTransaction.deactivate().end();
        }
        reporter.reset();
    }
}

