package co.elastic.apm.agent.concurrent;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.transaction.TraceContext;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

public class ExcludedExecutorClassTest extends AbstractInstrumentationTest {

    private ExecutorService executor;
    private Transaction transaction;

    @Before
    public void setUp() {
        executor = new ExecutorServiceWrapper(Executors.newFixedThreadPool(1));
        ExecutorInstrumentation.excludedClasses.add(ExecutorServiceWrapper.class.getName());
        transaction = tracer.startTransaction(TraceContext.asRoot(), null, null).withName("Transaction").activate();
    }

    @After
    public void tearDown() {
        transaction.deactivate().end();
        ExecutorInstrumentation.excludedClasses.remove(ExecutorServiceWrapper.class.getName());
    }

    @Test
    public void testExecutorExecute() throws Exception {
        assertThat(executor.submit(tracer::getActive).get()).isNull();
    }
}
