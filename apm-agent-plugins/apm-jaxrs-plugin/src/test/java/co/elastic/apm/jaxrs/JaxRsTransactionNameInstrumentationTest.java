package co.elastic.apm.jaxrs;

import co.elastic.apm.AbstractInstrumentationTest;
import co.elastic.apm.impl.transaction.Transaction;
import org.junit.jupiter.api.Test;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

import static org.assertj.core.api.Assertions.assertThat;

class JaxRsTransactionNameInstrumentationTest extends AbstractInstrumentationTest {

    @Test
    void testJaxRsTransactionName() {
        final Transaction request = tracer.startTransaction().withType("request").activate();
        try {
            new TestResource().testMethod();
        } finally {
            request.deactivate().end();
        }
        assertThat(reporter.getFirstTransaction().getName().toString()).isEqualTo("JaxRsTransactionNameInstrumentationTest$TestResource#testMethod");
    }

    @Path("test")
    public static class TestResource {

        @GET
        public String testMethod() {
            return "";
        }
    }
}
