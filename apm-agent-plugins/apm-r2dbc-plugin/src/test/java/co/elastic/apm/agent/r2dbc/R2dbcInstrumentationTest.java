package co.elastic.apm.agent.r2dbc;

import io.r2dbc.spi.ConnectionFactories;
import reactor.core.publisher.Mono;

public class R2dbcInstrumentationTest extends AbstractR2dbcInstrumentationTest {

    public R2dbcInstrumentationTest() {
        super(Mono.from(ConnectionFactories.get("r2dbc:h2:mem://test@localhost:111/testdb").create()).block(), "h2");
    }
}
