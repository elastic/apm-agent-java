package co.elastic.apm.agent.jaxws;

import co.elastic.apm.agent.AbstractInstrumentationTest;
import co.elastic.apm.agent.impl.Scope;
import co.elastic.apm.agent.impl.transaction.Transaction;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractJaxWsInstrumentationTest extends AbstractInstrumentationTest {

    protected BaseHelloWorldService helloWorldService;

    @Test
    void testTransactionName() {
        final Transaction transaction = tracer.startRootTransaction(getClass().getClassLoader());
        try (Scope scope = transaction.activateInScope()) {
            helloWorldService.sayHello();
        } finally {
            transaction.end();
        }
        assertThat(transaction.getNameAsString()).isEqualTo("HelloWorldServiceImpl#sayHello");
        assertThat(transaction.getFrameworkName()).isEqualTo("JAX-WS");
    }

    public interface BaseHelloWorldService {
        String sayHello();
    }
}
