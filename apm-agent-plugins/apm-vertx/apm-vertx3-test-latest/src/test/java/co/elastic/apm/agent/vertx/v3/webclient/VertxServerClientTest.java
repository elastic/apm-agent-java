package co.elastic.apm.agent.vertx.v3.webclient;

import co.elastic.apm.agent.vertx.helper.CommonVertxServerClientTest;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

public class VertxServerClientTest extends CommonVertxServerClientTest {
    @Override
    protected Handler<RoutingContext> getDefaultHandlerImpl() {
        return routingContext -> routingContext.response().end(DEFAULT_RESPONSE_BODY);
    }
}
