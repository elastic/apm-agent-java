package co.elastic.apm.agent.jaxrs;

import co.elastic.apm.agent.impl.ElasticApmTracer;
import co.elastic.apm.agent.impl.transaction.Transaction;
import co.elastic.apm.agent.jaxrs.resources.TestResource;
import co.elastic.apm.agent.jaxrs.resources.TestResourceWithPathOnAbstract;
import co.elastic.apm.agent.jaxrs.resources.TestResourceWithPathOnInterface;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;

import javax.ws.rs.core.Application;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base class for jax-rs plugin tests
 */
public class AbstractJaxRsTest extends JerseyTest {

    /**
     * @return configuration for the jersey test server. Includes all resource classes in the co.elastic.apm.agent.jaxrs.resources package.
     */
    public Application configure() {
        return new ResourceConfig(TestResource.class, TestResourceWithPathOnInterface.class, TestResourceWithPathOnAbstract.class);
    }

    /**
     * Make a GET request against the target path wrapped in an apm transaction.
     *
     * @param tracer the tracer to start/end the transaction with
     * @param path the path to make the get request against
     */
    protected void doRequest(ElasticApmTracer tracer, String path) {
        final Transaction request = tracer.startTransaction().withType("request").activate();
        try {
            assertThat(getClient().target(getBaseUri()).path(path).request().buildGet().invoke(String.class)).isEqualTo("ok");
        } finally {
            request.deactivate().end();
        }
    }
}
